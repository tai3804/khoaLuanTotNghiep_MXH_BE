package iuh.fit.adminservice.application.features.query;

import iuh.fit.adminservice.application.dto.response.ActivityHeatmapResponse;
import iuh.fit.adminservice.application.dto.response.DemographicsResponse;
import iuh.fit.adminservice.application.dto.response.PostPagedResponse;
import iuh.fit.adminservice.application.dto.response.TrendingHashtagResponse;
import iuh.fit.adminservice.application.dto.response.UserResponse;
import iuh.fit.adminservice.domain.entities.ModerationLog;
import iuh.fit.adminservice.domain.entities.Report;
import iuh.fit.adminservice.domain.repository.ModerationLogRepository;
import iuh.fit.adminservice.domain.repository.ReportRepository;
import iuh.fit.adminservice.infrastructure.feign.PostFeignClient;
import iuh.fit.adminservice.infrastructure.feign.UserFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetAnalyticsQuery {

    private final UserFeignClient userFeignClient;
    private final PostFeignClient postFeignClient;
    private final ReportRepository reportRepository;
    private final ModerationLogRepository moderationLogRepository;

    private LocalDateTime parseTimestamp(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            if (str.contains("Z") || str.contains("+")) {
                return Instant.parse(str).atZone(ZoneId.systemDefault()).toLocalDateTime();
            }
            return LocalDateTime.parse(str, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(str);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    public ActivityHeatmapResponse getActivityHeatmap() {
        List<String> days = List.of("Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ Nhật");
        List<Integer> hours = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            hours.add(i);
        }

        int[][] matrix = new int[7][24];
        long totalInteractions = 0;
        long eveningInteractions = 0;

        int[] dayTotals = new int[7];
        int[] hourTotals = new int[24];

        // 1. Process real timestamps & interactions from posts (including likes, comments, shares)
        try {
            PostPagedResponse postResp = postFeignClient.getPosts(1, 100);
            if (postResp != null && postResp.getData() != null) {
                for (var post : postResp.getData()) {
                    LocalDateTime dt = parseTimestamp(post.getCreatedAt());
                    if (dt != null) {
                        int dayIdx = dt.getDayOfWeek().getValue() - 1; // 0=Monday, 6=Sunday
                        int hour = dt.getHour();

                        // Total interactions for this post = 1 (bài viết) + likes + comments + shares
                        long postInteractions = 1L + Math.max(0, post.getLikeCount())
                                + Math.max(0, post.getCommentCount())
                                + Math.max(0, post.getShareCount());

                        matrix[dayIdx][hour] += postInteractions;
                        dayTotals[dayIdx] += postInteractions;
                        hourTotals[hour] += postInteractions;
                        totalInteractions += postInteractions;
                        if (hour >= 18 && hour <= 23) {
                            eveningInteractions += postInteractions;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not aggregate post timestamps for heatmap: {}", e.getMessage());
        }

        // 2. Process real timestamps from reports
        try {
            List<Report> reports = reportRepository.findAll();
            for (Report report : reports) {
                LocalDateTime dt = report.getCreatedAt();
                if (dt != null) {
                    int dayIdx = dt.getDayOfWeek().getValue() - 1;
                    int hour = dt.getHour();
                    matrix[dayIdx][hour]++;
                    dayTotals[dayIdx]++;
                    hourTotals[hour]++;
                    totalInteractions++;
                    if (hour >= 18 && hour <= 23) {
                        eveningInteractions++;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not aggregate report timestamps for heatmap: {}", e.getMessage());
        }

        // 3. Process real timestamps from moderation logs
        try {
            List<ModerationLog> logs = moderationLogRepository.findAll();
            for (ModerationLog mlog : logs) {
                LocalDateTime dt = mlog.getCreatedAt();
                if (dt != null) {
                    int dayIdx = dt.getDayOfWeek().getValue() - 1;
                    int hour = dt.getHour();
                    matrix[dayIdx][hour]++;
                    dayTotals[dayIdx]++;
                    hourTotals[hour]++;
                    totalInteractions++;
                    if (hour >= 18 && hour <= 23) {
                        eveningInteractions++;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not aggregate moderation log timestamps: {}", e.getMessage());
        }

        String peakDay = "Chưa xác định";
        String peakTimeRange = "--:--";
        double eveningRatio = 0.0;

        if (totalInteractions > 0) {
            int maxDayIdx = 0;
            int maxDayCount = -1;
            for (int d = 0; d < 7; d++) {
                if (dayTotals[d] > maxDayCount) {
                    maxDayCount = dayTotals[d];
                    maxDayIdx = d;
                }
            }
            peakDay = days.get(maxDayIdx);

            int maxHour = 0;
            int maxHourCount = -1;
            for (int h = 0; h < 24; h++) {
                if (hourTotals[h] > maxHourCount) {
                    maxHourCount = hourTotals[h];
                    maxHour = h;
                }
            }
            peakTimeRange = String.format("%02d:00 - %02d:00", maxHour, (maxHour + 1) % 24);

            eveningRatio = Math.round((eveningInteractions * 100.0 / totalInteractions) * 10.0) / 10.0;
        }

        return ActivityHeatmapResponse.builder()
                .days(days)
                .hours(hours)
                .matrix(matrix)
                .peakTimeRange(peakTimeRange)
                .peakDay(peakDay)
                .eveningActivityRatio(eveningRatio)
                .totalWeeklyInteractions(totalInteractions)
                .build();
    }

    public List<TrendingHashtagResponse> getTrendingHashtags() {
        Map<String, Long> tagCounts = new LinkedHashMap<>();

        try {
            PostPagedResponse postResp = postFeignClient.getPosts(1, 100);
            if (postResp != null && postResp.getData() != null) {
                Pattern hashtagPattern = Pattern.compile("#\\w+");
                for (var post : postResp.getData()) {
                    if (post.getContent() != null) {
                        Matcher matcher = hashtagPattern.matcher(post.getContent());
                        while (matcher.find()) {
                            String tag = matcher.group();
                            tagCounts.put(tag, tagCounts.getOrDefault(tag, 0L) + 1);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not extract hashtags from live posts: {}", e.getMessage());
        }

        List<TrendingHashtagResponse> list = new ArrayList<>();
        int rank = 1;

        for (Map.Entry<String, Long> entry : tagCounts.entrySet()) {
            long count = entry.getValue();
            list.add(TrendingHashtagResponse.builder()
                    .rank(rank++)
                    .tag(entry.getKey())
                    .category("Cộng đồng")
                    .postCount(count)
                    .growthPercentage(Math.round((count * 10.0) * 10.0) / 10.0)
                    .engagementScore(count * 10)
                    .status(count >= 5 ? "VIRAL" : "SAFE")
                    .build());
        }

        return list;
    }

    public DemographicsResponse getDemographics() {
        List<UserResponse> users = List.of();
        try {
            users = userFeignClient.getAllUsers();
        } catch (Exception e) {
            log.warn("Could not fetch users for demographics: {}", e.getMessage());
        }

        long totalUsers = users != null ? users.size() : 0;
        long activeCount = 0;
        long onlineCount = 0;

        long under18 = 0;
        long age18to24 = 0;
        long age25to34 = 0;
        long age35plus = 0;
        long unassignedAge = 0;

        long maleCount = 0;
        long femaleCount = 0;
        long otherGenderCount = 0;

        LocalDate now = LocalDate.now();

        if (users != null) {
            for (UserResponse u : users) {
                if (!"BANNED".equalsIgnoreCase(u.getStatus())) {
                    activeCount++;
                }
                if (Boolean.TRUE.equals(u.getIsOnline())) {
                    onlineCount++;
                }

                // Gender breakdown
                String g = u.getGender() != null ? u.getGender().toUpperCase() : "OTHER";
                if (g.contains("MALE") && !g.contains("FE")) {
                    maleCount++;
                } else if (g.contains("FEMALE")) {
                    femaleCount++;
                } else {
                    otherGenderCount++;
                }

                // Age group calculation from actual dateOfBirth
                if (u.getDateOfBirth() != null && !u.getDateOfBirth().isBlank()) {
                    try {
                        LocalDate dob = LocalDate.parse(u.getDateOfBirth());
                        int age = Period.between(dob, now).getYears();
                        if (age < 18) {
                            under18++;
                        } else if (age <= 24) {
                            age18to24++;
                        } else if (age <= 34) {
                            age25to34++;
                        } else {
                            age35plus++;
                        }
                    } catch (Exception e) {
                        unassignedAge++;
                    }
                } else {
                    unassignedAge++;
                }
            }
        }

        Map<String, Double> genderMap = new LinkedHashMap<>();
        if (totalUsers > 0) {
            genderMap.put("Nam", Math.round((maleCount * 100.0 / totalUsers) * 10.0) / 10.0);
            genderMap.put("Nữ", Math.round((femaleCount * 100.0 / totalUsers) * 10.0) / 10.0);
            genderMap.put("Khác", Math.round((otherGenderCount * 100.0 / totalUsers) * 10.0) / 10.0);
        } else {
            genderMap.put("Nam", 0.0);
            genderMap.put("Nữ", 0.0);
            genderMap.put("Khác", 0.0);
        }

        Map<String, Double> statusMap = new LinkedHashMap<>();
        if (totalUsers > 0) {
            double activePct = Math.round((activeCount * 100.0 / totalUsers) * 10.0) / 10.0;
            double bannedPct = Math.round(((totalUsers - activeCount) * 100.0 / totalUsers) * 10.0) / 10.0;
            statusMap.put("Hoạt động bình thường", activePct);
            statusMap.put("Đang bị khóa", bannedPct);
        } else {
            statusMap.put("Hoạt động bình thường", 0.0);
            statusMap.put("Đang bị khóa", 0.0);
        }

        Map<String, Long> ageGroups = new LinkedHashMap<>();
        ageGroups.put("Dưới 18 tuổi", under18);
        ageGroups.put("18 - 24 tuổi", age18to24);
        ageGroups.put("25 - 34 tuổi", age25to34);
        ageGroups.put("35+ tuổi", age35plus);
        if (unassignedAge > 0) {
            ageGroups.put("Chưa cập nhật ngày sinh", unassignedAge);
        }

        double retentionRate = totalUsers > 0
                ? Math.round((activeCount * 100.0 / totalUsers) * 10.0) / 10.0
                : 0.0;
        double onlineRatio = totalUsers > 0
                ? Math.round((onlineCount * 100.0 / totalUsers) * 10.0) / 10.0
                : 0.0;

        return DemographicsResponse.builder()
                .deviceDistribution(genderMap)
                .browserDistribution(statusMap)
                .ageGroupDistribution(ageGroups)
                .averageRetentionRate(retentionRate)
                .dailyActiveRatio(onlineRatio)
                .build();
    }
}
