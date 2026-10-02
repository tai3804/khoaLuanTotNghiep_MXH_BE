package iuh.fit.adminservice.presentation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.adminservice.application.dto.request.CreateReportRequest;
import iuh.fit.adminservice.application.dto.request.ProcessReportRequest;
import iuh.fit.adminservice.application.dto.response.ReportResponse;
import iuh.fit.adminservice.application.exception.AdminServiceErrorCode;
import iuh.fit.adminservice.application.features.report.commands.create_report.CreateReportCommand;
import iuh.fit.adminservice.application.features.report.commands.create_report.CreateReportCommandHandler;
import iuh.fit.adminservice.application.features.report.commands.create_report.CreateReportResult;
import iuh.fit.adminservice.application.features.report.commands.process_report.ProcessReportCommand;
import iuh.fit.adminservice.application.features.report.commands.process_report.ProcessReportCommandHandler;
import iuh.fit.adminservice.application.features.report.queries.get_report_detail.GetReportDetailQuery;
import iuh.fit.adminservice.application.features.report.queries.get_report_detail.GetReportDetailQueryHandler;
import iuh.fit.adminservice.application.features.report.queries.get_report_detail.GetReportDetailResult;
import iuh.fit.adminservice.application.features.report.queries.get_reports.GetReportsQuery;
import iuh.fit.adminservice.application.features.report.queries.get_reports.GetReportsQueryHandler;
import iuh.fit.adminservice.application.features.report.queries.get_reports.GetReportsResult;
import iuh.fit.adminservice.presentation.constants.ApiConstants;
import iuh.fit.adminservice.presentation.mapper.ModerationPresentationMapper;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.MODERATION_API + "/reports")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Violation Reporting", description = "APIs for reporting posts/comments/users and processing reported items")
@SecurityRequirement(name = "bearerAuth")
public class ModerationReportController {

    CreateReportCommandHandler createReportCommandHandler;
    ProcessReportCommandHandler processReportCommandHandler;
    GetReportsQueryHandler getReportsQueryHandler;
    GetReportDetailQueryHandler getReportDetailQueryHandler;
    ModerationPresentationMapper moderationPresentationMapper;
    JwtUtil jwtUtil;

    @PostMapping
    @Operation(summary = "Submit violation report", description = "Allows users to report a post, comment, or user for community guideline violations")
    public ResponseEntity<ApiResponse<ReportResponse>> createReport(@Valid @RequestBody CreateReportRequest request) {
        UUID reporterId = getCurrentUserId();
        CreateReportCommand command = moderationPresentationMapper.toCreateReportCommand(request, reporterId);
        CreateReportResult result = createReportCommandHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(moderationPresentationMapper.toResponse(result), "Report submitted successfully"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @Operation(summary = "Get list of reports (Moderator only)", description = "Retrieves paginated list of violation reports using BaseFilter (status, targetType)")
    public ResponseEntity<ApiResponse<List<ReportResponse>>> getReports(@ParameterObject @Valid @ModelAttribute BaseFilter filter) {
        GetReportsQuery query = GetReportsQuery.builder().filter(filter).build();
        PagedResponse<GetReportsResult> result = getReportsQueryHandler.handle(query);
        PagedResponse<ReportResponse> pagedResponse = moderationPresentationMapper.toPagedReportResponse(result);
        return ResponseEntity.ok(ApiResponse.paged(pagedResponse, "Reports retrieved successfully"));
    }

    @GetMapping("/{reportId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @Operation(summary = "Get report details (Moderator only)", description = "Retrieves details of a specific violation report")
    public ResponseEntity<ApiResponse<ReportResponse>> getReportDetail(@PathVariable UUID reportId) {
        GetReportDetailQuery query = GetReportDetailQuery.builder().reportId(reportId).build();
        GetReportDetailResult result = getReportDetailQueryHandler.handle(query);
        return ResponseEntity.ok(ApiResponse.success(moderationPresentationMapper.toResponse(result), "Report detail retrieved successfully"));
    }

    @PostMapping("/{reportId}/process")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @Operation(summary = "Process violation report (Moderator only)", description = "Resolves or dismisses a report and takes moderation action")
    public ResponseEntity<ApiResponse<Void>> processReport(
            @PathVariable UUID reportId,
            @Valid @RequestBody ProcessReportRequest request) {
        UUID moderatorId = null;
        try {
            moderatorId = getCurrentUserId();
        } catch (Exception ignored) {
            // Internal or system processor default
            moderatorId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        }
        ProcessReportCommand command = moderationPresentationMapper.toProcessReportCommand(request, reportId, moderatorId);
        processReportCommandHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(null, "Report processed successfully"));
    }

    private UUID getCurrentUserId() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(AdminServiceErrorCode.UNAUTHORIZED);
        }
        return UUID.fromString(userIdStr);
    }
}
