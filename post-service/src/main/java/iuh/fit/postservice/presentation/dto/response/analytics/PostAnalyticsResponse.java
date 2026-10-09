package iuh.fit.postservice.presentation.dto.response.analytics;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostAnalyticsResponse {
    long totalViews;
    long totalReach;
    long totalEngagements;
    long totalPosts;
    long totalLikes;
    long totalComments;
    long totalShares;
    double avgEngagementRate;
    List<DailyMetricDto> dailyMetrics;
    List<TopPostMetricDto> topPosts;
    String period;
}
