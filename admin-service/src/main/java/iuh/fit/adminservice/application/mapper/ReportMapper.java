package iuh.fit.adminservice.application.mapper;

import iuh.fit.adminservice.application.dto.response.ReportResponse;
import iuh.fit.adminservice.domain.entities.Report;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ReportMapper {
    ReportResponse toDto(Report report);
    List<ReportResponse> toDtoList(List<Report> reports);
    Report toEntity(ReportResponse reportResponse);
}

