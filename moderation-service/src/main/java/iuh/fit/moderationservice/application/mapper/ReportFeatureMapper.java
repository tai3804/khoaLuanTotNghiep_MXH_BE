package iuh.fit.moderationservice.application.mapper;

import iuh.fit.moderationservice.domain.entities.Report;
import iuh.fit.moderationservice.application.features.report.commands.create_report.CreateReportResult;
import iuh.fit.moderationservice.application.features.report.queries.get_reports.GetReportsResult;
import iuh.fit.moderationservice.application.features.report.queries.get_report_detail.GetReportDetailResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ReportFeatureMapper {

    @Mapping(target = "reportId", source = "id")
    CreateReportResult toCreateReportResult(Report report);

    @Mapping(target = "reportId", source = "id")
    GetReportsResult toGetReportsResult(Report report);

    @Mapping(target = "reportId", source = "id")
    GetReportDetailResult toGetReportDetailResult(Report report);
}
