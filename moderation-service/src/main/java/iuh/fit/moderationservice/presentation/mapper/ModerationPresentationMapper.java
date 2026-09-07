package iuh.fit.moderationservice.presentation.mapper;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.moderationservice.application.features.moderation.commands.moderate_post.ModeratePostCommand;
import iuh.fit.moderationservice.application.features.moderation.queries.get_moderation_logs.GetModerationLogsResult;
import iuh.fit.moderationservice.application.features.report.commands.create_report.CreateReportCommand;
import iuh.fit.moderationservice.application.features.report.commands.create_report.CreateReportResult;
import iuh.fit.moderationservice.application.features.report.commands.process_report.ProcessReportCommand;
import iuh.fit.moderationservice.application.features.report.queries.get_report_detail.GetReportDetailResult;
import iuh.fit.moderationservice.application.features.report.queries.get_reports.GetReportsResult;
import iuh.fit.moderationservice.presentation.dto.request.CreateReportRequest;
import iuh.fit.moderationservice.presentation.dto.request.ModeratePostRequest;
import iuh.fit.moderationservice.presentation.dto.request.ProcessReportRequest;
import iuh.fit.moderationservice.presentation.dto.response.ModerationLogResponse;
import iuh.fit.moderationservice.presentation.dto.response.ReportResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ModerationPresentationMapper {

    CreateReportCommand toCreateReportCommand(CreateReportRequest request, UUID reporterId);

    ProcessReportCommand toProcessReportCommand(ProcessReportRequest request, UUID reportId, UUID moderatorId);

    ModeratePostCommand toModeratePostCommand(ModeratePostRequest request, UUID postId, UUID moderatorId);

    ReportResponse toResponse(CreateReportResult result);

    ReportResponse toResponse(GetReportsResult result);

    ReportResponse toResponse(GetReportDetailResult result);

    List<ReportResponse> toReportResponseList(List<GetReportsResult> results);

    PagedResponse<ReportResponse> toPagedReportResponse(PagedResponse<GetReportsResult> pagedResult);

    ModerationLogResponse toResponse(GetModerationLogsResult result);

    List<ModerationLogResponse> toLogResponseList(List<GetModerationLogsResult> results);

    PagedResponse<ModerationLogResponse> toPagedLogResponse(PagedResponse<GetModerationLogsResult> pagedResult);
}
