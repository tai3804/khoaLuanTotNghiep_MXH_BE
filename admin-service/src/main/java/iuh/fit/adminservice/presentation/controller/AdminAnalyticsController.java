package iuh.fit.adminservice.presentation.controller;

import iuh.fit.adminservice.application.dto.response.ActivityHeatmapResponse;
import iuh.fit.adminservice.application.dto.response.DemographicsResponse;
import iuh.fit.adminservice.application.dto.response.TrendingHashtagResponse;
import iuh.fit.adminservice.application.features.query.GetAnalyticsQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
public class AdminAnalyticsController {

    private final GetAnalyticsQuery getAnalyticsQuery;

    @GetMapping("/heatmap")
    public ResponseEntity<ActivityHeatmapResponse> getActivityHeatmap() {
        return ResponseEntity.ok(getAnalyticsQuery.getActivityHeatmap());
    }

    @GetMapping("/trends")
    public ResponseEntity<List<TrendingHashtagResponse>> getTrendingHashtags() {
        return ResponseEntity.ok(getAnalyticsQuery.getTrendingHashtags());
    }

    @GetMapping("/demographics")
    public ResponseEntity<DemographicsResponse> getDemographics() {
        return ResponseEntity.ok(getAnalyticsQuery.getDemographics());
    }
}
