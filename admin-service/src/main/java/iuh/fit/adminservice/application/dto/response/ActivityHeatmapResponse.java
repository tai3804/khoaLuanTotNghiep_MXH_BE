package iuh.fit.adminservice.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityHeatmapResponse {
    private List<String> days;
    private List<Integer> hours;
    private int[][] matrix;
    private String peakTimeRange;
    private String peakDay;
    private double eveningActivityRatio;
    private long totalWeeklyInteractions;
}
