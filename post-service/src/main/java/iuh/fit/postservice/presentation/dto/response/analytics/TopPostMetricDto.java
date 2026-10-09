package iuh.fit.postservice.presentation.dto.response.analytics;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TopPostMetricDto {
    UUID id;
    String content;
    String firstMediaUrl;
    LocalDateTime createdAt;
    long viewCount;
    long likeCount;
    long commentCount;
    long shareCount;
    double engagementRate;
}
