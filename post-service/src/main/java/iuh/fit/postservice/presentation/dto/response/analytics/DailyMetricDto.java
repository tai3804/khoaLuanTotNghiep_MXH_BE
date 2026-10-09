package iuh.fit.postservice.presentation.dto.response.analytics;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DailyMetricDto {
    String date;
    long views;
    long likes;
    long comments;
    long shares;
    long engagements;
}
