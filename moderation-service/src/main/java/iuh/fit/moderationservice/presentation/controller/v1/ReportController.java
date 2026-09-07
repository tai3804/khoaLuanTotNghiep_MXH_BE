package iuh.fit.moderationservice.presentation.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.moderationservice.application.exception.ModerationServiceErrorCode;
import iuh.fit.moderationservice.application.features.report.commands.create_report.CreateReportCommand;
import iuh.fit.moderationservice.application.features.report.commands.create_report.CreateReportCommandHandler;
import iuh.fit.moderationservice.application.features.report.commands.create_report.CreateReportResult;
import iuh.fit.moderationservice.application.features.report.commands.process_report.ProcessReportCommand;
import iuh.fit.moderationservice.application.features.report.commands.process_report.ProcessReportCommandHandler;
import iuh.fit.moderationservice.application.features.report.queries.get_report_detail.GetReportDetailQuery;
import iuh.fit.moderationservice.application.features.report.queries.get_report_detail.GetReportDetailQueryHandler;
import iuh.fit.moderationservice.application.features.report.queries.get_report_detail.GetReportDetailResult;
import iuh.fit.moderationservice.application.features.report.queries.get_reports.GetReportsQuery;
import iuh.fit.moderationservice.application.features.report.queries.get_reports.GetReportsQueryHandler;
import iuh.fit.moderationservice.application.features.report.queries.get_reports.GetReportsResult;
import iuh.fit.moderationservice.presentation.constants.ApiConstants;
import iuh.fit.moderationservice.presentation.dto.request.CreateReportRequest;
import iuh.fit.moderationservice.presentation.dto.request.ProcessReportRequest;
import iuh.fit.moderationservice.presentation.dto.response.ReportResponse;
import iuh.fit.moderationservice.presentation.mapper.ModerationPresentationMapper;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.MODERATION_API + "/reports")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Violation Reporting", description = "APIs for reporting posts/comments/users and processing reported items")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

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
    @Operation(summary = "Get list of reports (Moderator only)", description = "Retrieves paginated list of violation reports using BaseFilter (status, targetType)")
    public ResponseEntity<ApiResponse<List<ReportResponse>>> getReports(@ParameterObject @Valid @ModelAttribute BaseFilter filter) {
        GetReportsQuery query = GetReportsQuery.builder().filter(filter).build();
        PagedResponse<GetReportsResult> result = getReportsQueryHandler.handle(query);
        PagedResponse<ReportResponse> pagedResponse = moderationPresentationMapper.toPagedReportResponse(result);
        return ResponseEntity.ok(ApiResponse.paged(pagedResponse, "Reports retrieved successfully"));
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "Get report details (Moderator only)", description = "Retrieves details of a specific violation report")
    public ResponseEntity<ApiResponse<ReportResponse>> getReportDetail(@PathVariable UUID reportId) {
        GetReportDetailQuery query = GetReportDetailQuery.builder().reportId(reportId).build();
        GetReportDetailResult result = getReportDetailQueryHandler.handle(query);
        return ResponseEntity.ok(ApiResponse.success(moderationPresentationMapper.toResponse(result), "Report detail retrieved successfully"));
    }

    @PostMapping("/{reportId}/process")
    @Operation(summary = "Process violation report (Moderator only)", description = "Resolves or dismisses a report and takes moderation action")
    public ResponseEntity<ApiResponse<Void>> processReport(
            @PathVariable UUID reportId,
            @Valid @RequestBody ProcessReportRequest request) {
        UUID moderatorId = getCurrentUserId();
        ProcessReportCommand command = moderationPresentationMapper.toProcessReportCommand(request, reportId, moderatorId);
        processReportCommandHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(null, "Report processed successfully"));
    }

    private UUID getCurrentUserId() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(ModerationServiceErrorCode.UNAUTHORIZED);
        }
        return UUID.fromString(userIdStr);
    }
}
