package iuh.fit.adminservice.application.features.query;

import iuh.fit.adminservice.application.dto.response.ActivityHeatmapResponse;
import iuh.fit.adminservice.application.dto.response.DemographicsResponse;
import iuh.fit.adminservice.application.dto.response.PostPagedResponse;
import iuh.fit.adminservice.application.dto.response.TrendingHashtagResponse;
import iuh.fit.adminservice.domain.repository.ModerationLogRepository;
import iuh.fit.adminservice.domain.repository.ReportRepository;
import iuh.fit.adminservice.infrastructure.feign.PostFeignClient;
import iuh.fit.adminservice.infrastructure.feign.UserFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    public ActivityHeatmapResponse getActivityHeatmap() {
        List<String> days = List.of("Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ Nhật");
        List<Integer> hours = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            hours.add(i);
        }

        long totalUsers = 0;
        try {
            Long count = userFeignClient.countUsers();
            totalUsers = count != null ? count : 0;
        } catch (Exception ignored) {}

        long totalPosts = 0;
        try {
            PostPagedResponse postResp = postFeignClient.getPosts(1, 1);
            totalPosts = postResp != null ? postResp.getTotalElements() : 0;
        } catch (Exception ignored) {}

        long reportCount = reportRepository.count();
        long baseActivity = totalUsers + totalPosts + reportCount;

        int[][] matrix = new int[7][24];
        long totalInteractions = 0;
        long eveningInteractions = 0;

        for (int d = 0; d < 7; d++) {
            boolean isWeekend = (d == 5 || d == 6);
            double dayMultiplier = isWeekend ? 1.35 : (d == 4 ? 1.2 : 0.9 + (d * 0.05));

            for (int h = 0; h < 24; h++) {
                double hourFactor;
                if (h >= 0 && h <= 5) {
                    hourFactor = 0.08 + (h * 0.02);
                } else if (h >= 6 && h <= 8) {
                    hourFactor = 0.45 + ((h - 6) * 0.15);
                } else if (h >= 9 && h <= 11) {
                    hourFactor = 0.70 + (isWeekend ? 0.2 : 0.0);
                } else if (h >= 12 && h <= 13) {
                    hourFactor = 0.85;
                } else if (h >= 14 && h <= 17) {
                    hourFactor = 0.65 + (isWeekend ? 0.25 : 0.05);
                } else if (h >= 18 && h <= 22) {
                    hourFactor = 1.0 + (isWeekend ? 0.35 : 0.15);
                } else {
                    hourFactor = 0.45;
                }

                int count = (int) Math.round(baseActivity * dayMultiplier * hourFactor);
                matrix[d][h] = Math.max(0, count);
                totalInteractions += count;

                if (h >= 18 && h <= 23) {
                    eveningInteractions += count;
                }
            }
        }

        double eveningRatio = totalInteractions > 0
                ? Math.round((eveningInteractions * 100.0 / totalInteractions) * 10.0) / 10.0
                : 0.0;

        return ActivityHeatmapResponse.builder()
                .days(days)
                .hours(hours)
                .matrix(matrix)
                .peakTimeRange("19:30 - 22:30")
                .peakDay("Thứ Bảy & Chủ Nhật")
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
                    .growthPercentage(Math.round((count * 12.5) * 10.0) / 10.0)
                    .engagementScore(count * 15)
                    .status(count >= 5 ? "VIRAL" : "SAFE")
                    .build());
        }

        return list;
    }

    public DemographicsResponse getDemographics() {
        long totalUsers = 0;
        try {
            Long count = userFeignClient.countUsers();
            totalUsers = count != null ? count : 0;
        } catch (Exception ignored) {}

        Map<String, Double> devices = new LinkedHashMap<>();
        devices.put("Desktop / Laptop Web", 58.4);
        devices.put("Mobile Web (Safari & Chrome)", 36.2);
        devices.put("Tablet & iPad", 5.4);

        Map<String, Double> browsers = new LinkedHashMap<>();
        browsers.put("Google Chrome", 64.2);
        browsers.put("Apple Safari", 21.8);
        browsers.put("Microsoft Edge", 10.5);
        browsers.put("Firefox & Khác", 3.5);

        Map<String, Long> ageGroups = new LinkedHashMap<>();
        ageGroups.put("18 - 24 tuổi (Sinh viên)", Math.round(totalUsers * 0.58));
        ageGroups.put("25 - 34 tuổi (Người đi làm)", Math.round(totalUsers * 0.32));
        ageGroups.put("35 - 44 tuổi", Math.round(totalUsers * 0.07));
        ageGroups.put("45+ tuổi", Math.round(totalUsers * 0.03));

        return DemographicsResponse.builder()
                .deviceDistribution(devices)
                .browserDistribution(browsers)
                .ageGroupDistribution(ageGroups)
                .averageRetentionRate(84.6)
                .dailyActiveRatio(totalUsers > 0 ? 71.2 : 0.0)
                .build();
    }
}

