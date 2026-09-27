package iuh.fit.adminservice.application.mapper;

import iuh.fit.adminservice.application.features.moderation.queries.get_moderation_logs.GetModerationLogsResult;
import iuh.fit.adminservice.domain.entities.ModerationLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ModerationFeatureMapper {

    @Mapping(target = "logId", source = "id")
    GetModerationLogsResult toGetModerationLogsResult(ModerationLog log);
}
