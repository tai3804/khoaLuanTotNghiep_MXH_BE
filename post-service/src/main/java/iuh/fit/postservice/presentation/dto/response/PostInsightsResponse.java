package iuh.fit.postservice.presentation.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostInsightsResponse {
    UUID postId;
    String contentSnippet;
    LocalDateTime publishedAt;
    long views;
    long reach;
    long likes;
    long comments;
    long shares;
    long saves;
    double engagementRate;
    double avgWatchRetention;
    Map<String, Double> trafficSources;
    Map<String, Double> audienceGender;
    List<HourlyStat> hourlyViews;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class HourlyStat {
        String hour;
        long views;
        long engagements;
    }
}
