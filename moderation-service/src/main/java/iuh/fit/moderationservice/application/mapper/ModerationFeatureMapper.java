package iuh.fit.moderationservice.application.mapper;

import iuh.fit.moderationservice.domain.entities.ModerationLog;
import iuh.fit.moderationservice.application.features.moderation.queries.get_moderation_logs.GetModerationLogsResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ModerationFeatureMapper {

    @Mapping(target = "logId", source = "id")
    GetModerationLogsResult toGetModerationLogsResult(ModerationLog log);
}
