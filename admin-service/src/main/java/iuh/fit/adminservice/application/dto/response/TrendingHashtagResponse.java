package iuh.fit.adminservice.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendingHashtagResponse {
    private int rank;
    private String tag;
    private String category;
    private long postCount;
    private double growthPercentage;
    private long engagementScore;
    private String status;
}
