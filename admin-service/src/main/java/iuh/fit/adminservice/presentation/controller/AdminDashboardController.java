package iuh.fit.adminservice.presentation.controller;

import iuh.fit.adminservice.application.dto.response.DashboardStatResponse;
import iuh.fit.adminservice.application.dto.response.InteractionStatResponse;
import iuh.fit.adminservice.application.dto.response.ReportCategoryStatResponse;
import iuh.fit.adminservice.application.dto.response.UserGrowthStatResponse;
import iuh.fit.adminservice.application.features.query.GetDashboardStatsQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {
    
    private final GetDashboardStatsQuery getDashboardStatsQuery;

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatResponse> getStats() {
        return ResponseEntity.ok(getDashboardStatsQuery.execute());
    }

    @GetMapping("/growth")
    public ResponseEntity<List<UserGrowthStatResponse>> getGrowth() {
        return ResponseEntity.ok(getDashboardStatsQuery.getUserGrowth());
    }

    @GetMapping("/interactions")
    public ResponseEntity<List<InteractionStatResponse>> getInteractions() {
        return ResponseEntity.ok(getDashboardStatsQuery.getInteractions());
    }

    @GetMapping("/report-categories")
    public ResponseEntity<List<ReportCategoryStatResponse>> getReportCategories() {
        return ResponseEntity.ok(getDashboardStatsQuery.getReportCategories());
    }
}


