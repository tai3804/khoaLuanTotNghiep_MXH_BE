package iuh.fit.adminservice.presentation.mapper;

import iuh.fit.adminservice.application.dto.request.CreateReportRequest;
import iuh.fit.adminservice.application.dto.request.ModeratePostRequest;
import iuh.fit.adminservice.application.dto.request.ProcessReportRequest;
import iuh.fit.adminservice.application.dto.response.ModerationLogResponse;
import iuh.fit.adminservice.application.dto.response.ReportResponse;
import iuh.fit.adminservice.application.features.moderation.commands.moderate_post.ModeratePostCommand;
import iuh.fit.adminservice.application.features.moderation.queries.get_moderation_logs.GetModerationLogsResult;
import iuh.fit.adminservice.application.features.report.commands.create_report.CreateReportCommand;
import iuh.fit.adminservice.application.features.report.commands.create_report.CreateReportResult;
import iuh.fit.adminservice.application.features.report.commands.process_report.ProcessReportCommand;
import iuh.fit.adminservice.application.features.report.queries.get_report_detail.GetReportDetailResult;
import iuh.fit.adminservice.application.features.report.queries.get_reports.GetReportsResult;
import iuh.fit.commonframework.application.dto.PagedResponse;
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

    default PagedResponse<ReportResponse> toPagedReportResponse(PagedResponse<GetReportsResult> pagedResult) {
        if (pagedResult == null) return null;
        return PagedResponse.map(pagedResult, toReportResponseList(pagedResult.getContent()));
    }

    ModerationLogResponse toResponse(GetModerationLogsResult result);

    List<ModerationLogResponse> toLogResponseList(List<GetModerationLogsResult> results);

    default PagedResponse<ModerationLogResponse> toPagedLogResponse(PagedResponse<GetModerationLogsResult> pagedResult) {
        if (pagedResult == null) return null;
        return PagedResponse.map(pagedResult, toLogResponseList(pagedResult.getContent()));
    }
}
