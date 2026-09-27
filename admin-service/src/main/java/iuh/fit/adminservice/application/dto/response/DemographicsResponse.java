package iuh.fit.adminservice.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemographicsResponse {
    private Map<String, Double> deviceDistribution;
    private Map<String, Double> browserDistribution;
    private Map<String, Long> ageGroupDistribution;
    private double averageRetentionRate;
    private double dailyActiveRatio;
}
