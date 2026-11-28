package iuh.fit.adminservice.application.features.query;

import iuh.fit.adminservice.application.dto.response.DashboardStatResponse;
import iuh.fit.adminservice.application.dto.response.InteractionStatResponse;
import iuh.fit.adminservice.application.dto.response.PostPagedResponse;
import iuh.fit.adminservice.application.dto.response.ReportCategoryStatResponse;
import iuh.fit.adminservice.application.dto.response.UserGrowthStatResponse;
import iuh.fit.adminservice.application.dto.response.UserResponse;
import iuh.fit.adminservice.domain.entities.Report;
import iuh.fit.adminservice.domain.enums.ReportCategory;
import iuh.fit.adminservice.domain.enums.ReportStatus;
import iuh.fit.adminservice.domain.repository.ReportRepository;
import iuh.fit.adminservice.infrastructure.feign.PostFeignClient;
import iuh.fit.adminservice.infrastructure.feign.UserFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Số liệu trang tổng quan. Bốn API (stats, growth, interactions, report-categories) được gọi song song mỗi lần mở trang: danh
 * sách người dùng (tải từ user-service) được dùng chung trong {@link #USERS_TTL_MS} ms thay vì mỗi API tự tải lại; số bài,
 * bình luận, lượt cảm xúc theo ngày do post-service đếm bằng GROUP BY; báo cáo theo loại đếm bằng một truy vấn gom nhóm.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetDashboardStatsQuery {
    static final long USERS_TTL_MS = 15_000;

    private final UserFeignClient userFeignClient;
    private final PostFeignClient postFeignClient;
    private final ReportRepository reportRepository;

    private final Object usersLock = new Object();
    private volatile List<UserResponse> cachedUsers;
    private volatile long cachedUsersAt;

    /** Danh sách người dùng dùng chung giữa các API của trang tổng quan trong {@link #USERS_TTL_MS} ms. */
    private List<UserResponse> users() {
        List<UserResponse> c = cachedUsers;
        if (c != null && System.currentTimeMillis() - cachedUsersAt < USERS_TTL_MS) return c;
        synchronized (usersLock) {
            if (cachedUsers != null && System.currentTimeMillis() - cachedUsersAt < USERS_TTL_MS) return cachedUsers;
            List<UserResponse> fresh = userFeignClient.getAllUsers();
            cachedUsers = fresh != null ? fresh : List.of();
            cachedUsersAt = System.currentTimeMillis();
            return cachedUsers;
        }
    }

    /** Số bài / bình luận / cảm xúc mới theo ngày (yyyy-MM-dd -> [posts, comments, reactions]); lỗi thì rỗng. */
    private Map<LocalDate, long[]> dailyActivity(int days) {
        Map<LocalDate, long[]> out = new HashMap<>();
        try {
            List<Map<String, Object>> rows = postFeignClient.getDailyActivity(days);
            if (rows != null) {
                for (Map<String, Object> r : rows) {
                    LocalDate d = parseDate(String.valueOf(r.get("date")));
                    if (d != null) out.put(d, new long[]{num(r.get("posts")), num(r.get("comments")), num(r.get("reactions"))});
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch daily activity from post-service: {}", e.getMessage());
        }
        return out;
    }

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private LocalDate parseDate(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            if (str.contains("Z") || str.contains("+")) {
                return Instant.parse(str).atZone(ZoneId.systemDefault()).toLocalDate();
            }
            if (str.contains("T")) {
                return LocalDateTime.parse(str, DateTimeFormatter.ISO_DATE_TIME).toLocalDate();
            }
            return LocalDate.parse(str);
        } catch (Exception e) {
            return null;
        }
    }

    public DashboardStatResponse execute() {
        DashboardStatResponse stat = new DashboardStatResponse();
        LocalDate today = LocalDate.now();

        List<UserResponse> users = List.of();
        long totalUsers = 0;
        long newUsersToday = 0;
        long onlineUsers = 0;

        try {
            users = users();
            if (users != null) {
                totalUsers = users.size();
                for (UserResponse u : users) {
                    if (Boolean.TRUE.equals(u.getIsOnline())) {
                        onlineUsers++;
                    }
                    LocalDate regDate = parseDate(u.getCreatedAt());
                    if (regDate != null && regDate.isEqual(today)) {
                        newUsersToday++;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch users for dashboard stats: {}", e.getMessage());
        }

        long totalPosts = 0;
        try {
            // Chỉ cần tổng số: lấy trang 1 cỡ 1 thay vì tải 100 bài
            PostPagedResponse postResp = postFeignClient.getPosts(1, 1);
            if (postResp != null) {
                totalPosts = postResp.getTotalElements();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch posts for dashboard stats: {}", e.getMessage());
        }
        long newPostsToday = dailyActivity(1).getOrDefault(today, new long[3])[0];

        long totalReports = 0;
        long pendingReports = 0;
        long resolvedReportsToday = 0;

        try {
            totalReports = reportRepository.count();
            pendingReports = reportRepository.countByStatus(ReportStatus.PENDING);
            LocalDateTime startOfToday = today.atStartOfDay();
            LocalDateTime endOfToday = today.atTime(23, 59, 59, 999999999);
            resolvedReportsToday = reportRepository.countByStatusInAndResolvedAtBetween(
                    List.of(ReportStatus.RESOLVED, ReportStatus.DISMISSED),
                    startOfToday,
                    endOfToday
            );
        } catch (Exception e) {
            log.warn("Failed to fetch report counts from repository: {}", e.getMessage());
        }

        stat.setTotalUsers(totalUsers);
        stat.setTotalPosts(totalPosts);
        stat.setTotalReports(totalReports);
        stat.setPendingReports(pendingReports);
        stat.setResolvedReportsToday(resolvedReportsToday);
        stat.setNewUsersToday(newUsersToday);
        stat.setNewPostsToday(newPostsToday);
        stat.setActiveUsersNow(onlineUsers);

        return stat;
    }

    public List<UserGrowthStatResponse> getUserGrowth() {
        List<UserGrowthStatResponse> list = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM");
        LocalDate today = LocalDate.now();

        List<UserResponse> users = List.of();
        try {
            users = users();
        } catch (Exception e) {
            log.warn("Failed to fetch users for growth chart: {}", e.getMessage());
        }

        Map<LocalDate, Long> newUsersPerDay = new HashMap<>();
        for (int i = 6; i >= 0; i--) {
            newUsersPerDay.put(today.minusDays(i), 0L);
        }

        long usersWithNullDate = 0;
        LocalDate startOfWindow = today.minusDays(6);
        long baselineUsersBeforeWindow = 0;

        if (users != null) {
            for (UserResponse u : users) {
                LocalDate d = parseDate(u.getCreatedAt());
                if (d == null) {
                    usersWithNullDate++;
                } else if (newUsersPerDay.containsKey(d)) {
                    newUsersPerDay.put(d, newUsersPerDay.get(d) + 1);
                } else if (d.isBefore(startOfWindow)) {
                    baselineUsersBeforeWindow++;
                }
            }
        }

        long runningCumulative = baselineUsersBeforeWindow + usersWithNullDate;

        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            long newUsers = newUsersPerDay.getOrDefault(d, 0L);
            runningCumulative += newUsers;
            list.add(new UserGrowthStatResponse(d.format(formatter), newUsers, runningCumulative));
        }
        return list;
    }

    public List<InteractionStatResponse> getInteractions() {
        List<InteractionStatResponse> list = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM");
        LocalDate today = LocalDate.now();

        // Số thật của từng ngày: bài viết, bình luận, lượt cảm xúc (trước đây bình luận và lượt thích luôn là 0)
        Map<LocalDate, long[]> activity = dailyActivity(7);
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            long[] c = activity.getOrDefault(d, new long[3]);
            list.add(new InteractionStatResponse(d.format(formatter), c[0], c[1], c[2]));
        }
        return list;
    }

    public List<ReportCategoryStatResponse> getReportCategories() {
        List<ReportCategoryStatResponse> list = new ArrayList<>();
        Map<ReportCategory, Long> categoryCount = new LinkedHashMap<>();
        for (ReportCategory cat : ReportCategory.values()) {
            categoryCount.put(cat, 0L);
        }

        try {
            for (Object[] row : reportRepository.countGroupByReason()) {
                String reason = row[0] != null ? row[0].toString() : "OTHER";
                ReportCategory cat = ReportCategory.fromReason(reason);
                categoryCount.put(cat, categoryCount.get(cat) + ((Number) row[1]).longValue());
            }
        } catch (Exception e) {
            log.warn("Failed to aggregate report categories: {}", e.getMessage());
        }

        long total = categoryCount.values().stream().mapToLong(Long::longValue).sum();

        for (Map.Entry<ReportCategory, Long> entry : categoryCount.entrySet()) {
            ReportCategory cat = entry.getKey();
            long count = entry.getValue();
            double percentage = total > 0 ? (count * 100.0 / total) : 0.0;
            list.add(new ReportCategoryStatResponse(
                    cat.getDisplayName(),
                    count,
                    Math.round(percentage * 10.0) / 10.0,
                    cat.getColor()
            ));
        }

        return list;
    }
}
