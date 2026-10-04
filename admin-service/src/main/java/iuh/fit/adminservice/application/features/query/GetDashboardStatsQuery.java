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

@Slf4j
@Service
@RequiredArgsConstructor
public class GetDashboardStatsQuery {
    private final UserFeignClient userFeignClient;
    private final PostFeignClient postFeignClient;
    private final ReportRepository reportRepository;

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
            users = userFeignClient.getAllUsers();
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
        long newPostsToday = 0;
        try {
            PostPagedResponse postResp = postFeignClient.getPosts(1, 100);
            if (postResp != null) {
                totalPosts = postResp.getTotalElements();
                if (postResp.getData() != null) {
                    for (var post : postResp.getData()) {
                        LocalDate postDate = parseDate(post.getCreatedAt());
                        if (postDate != null && postDate.isEqual(today)) {
                            newPostsToday++;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch posts for dashboard stats: {}", e.getMessage());
        }

        long totalReports = 0;
        long pendingReports = 0;
        long resolvedReports = 0;

        try {
            totalReports = reportRepository.count();
            pendingReports = reportRepository.countByStatus(ReportStatus.PENDING);
            resolvedReports = Math.max(0, totalReports - pendingReports);
        } catch (Exception e) {
            log.warn("Failed to fetch report counts from repository: {}", e.getMessage());
        }

        stat.setTotalUsers(totalUsers);
        stat.setTotalPosts(totalPosts);
        stat.setTotalReports(totalReports);
        stat.setPendingReports(pendingReports);
        stat.setResolvedReportsToday(resolvedReports);
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
            users = userFeignClient.getAllUsers();
        } catch (Exception ignored) {}

        Map<LocalDate, Long> newUsersPerDay = new HashMap<>();
        for (int i = 6; i >= 0; i--) {
            newUsersPerDay.put(today.minusDays(i), 0L);
        }

        if (users != null) {
            for (UserResponse u : users) {
                LocalDate d = parseDate(u.getCreatedAt());
                if (d != null && newUsersPerDay.containsKey(d)) {
                    newUsersPerDay.put(d, newUsersPerDay.get(d) + 1);
                }
            }
        }

        long cumulativeUsers = users != null ? users.size() : 0;

        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            long newUsers = newUsersPerDay.getOrDefault(d, 0L);
            list.add(new UserGrowthStatResponse(d.format(formatter), newUsers, cumulativeUsers));
        }
        return list;
    }

    public List<InteractionStatResponse> getInteractions() {
        List<InteractionStatResponse> list = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM");
        LocalDate today = LocalDate.now();

        Map<LocalDate, Long> postsPerDay = new HashMap<>();
        for (int i = 6; i >= 0; i--) {
            postsPerDay.put(today.minusDays(i), 0L);
        }

        try {
            PostPagedResponse postResp = postFeignClient.getPosts(1, 100);
            if (postResp != null && postResp.getData() != null) {
                for (var post : postResp.getData()) {
                    LocalDate d = parseDate(post.getCreatedAt());
                    if (d != null && postsPerDay.containsKey(d)) {
                        postsPerDay.put(d, postsPerDay.get(d) + 1);
                    }
                }
            }
        } catch (Exception ignored) {}

        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            long posts = postsPerDay.getOrDefault(d, 0L);
            list.add(new InteractionStatResponse(d.format(formatter), posts, 0L, 0L));
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
            List<Report> reports = reportRepository.findAll();
            for (Report r : reports) {
                String reason = r.getReason() != null ? r.getReason().name() : "OTHER";
                ReportCategory cat = ReportCategory.fromReason(reason);
                categoryCount.put(cat, categoryCount.get(cat) + 1);
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
