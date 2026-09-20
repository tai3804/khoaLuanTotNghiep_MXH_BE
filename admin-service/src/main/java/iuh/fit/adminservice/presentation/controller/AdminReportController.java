package iuh.fit.adminservice.presentation.controller;

import iuh.fit.adminservice.application.dto.request.ResolveReportRequest;
import iuh.fit.adminservice.application.dto.response.ReportResponse;
import iuh.fit.adminservice.application.features.command.ResolveReportCommand;
import iuh.fit.adminservice.application.features.query.GetReportsQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import iuh.fit.adminservice.infrastructure.feign.PostFeignClient;
import iuh.fit.adminservice.infrastructure.feign.UserFeignClient;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final GetReportsQuery getReportsQuery;
    private final ResolveReportCommand resolveReportCommand;
    private final PostFeignClient postFeignClient;
    private final UserFeignClient userFeignClient;

    @GetMapping
    public ResponseEntity<List<ReportResponse>> getAllReports() {
        return ResponseEntity.ok(getReportsQuery.execute());
    }

    @GetMapping("/pending")
    public ResponseEntity<List<ReportResponse>> getPendingReports() {
        return ResponseEntity.ok(getReportsQuery.executePending());
    }

    @PutMapping("/{id}/resolve")
    public ResponseEntity<Void> resolveReport(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true", required = false) boolean deleteTarget,
            @RequestBody(required = false) ResolveReportRequest request) {
        boolean shouldDelete = request != null ? request.isDeleteTarget() : deleteTarget;
        resolveReportCommand.execute(id, shouldDelete);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/target/{type}/{targetId}")
    public ResponseEntity<Object> getReportTarget(@PathVariable String type, @PathVariable String targetId) {
        try {
            if ("POST".equalsIgnoreCase(type)) {
                return ResponseEntity.ok(postFeignClient.getPostById(targetId));
            } else if ("USER".equalsIgnoreCase(type)) {
                // Return null or placeholder as user details are usually in UserFeignClient
                return ResponseEntity.ok().build(); 
            }
        } catch(Exception e) {
            // ignore
        }
        return ResponseEntity.notFound().build();
    }
}

