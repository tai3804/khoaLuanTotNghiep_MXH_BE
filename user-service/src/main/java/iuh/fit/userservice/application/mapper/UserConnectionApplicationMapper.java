package iuh.fit.userservice.application.mapper;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.userservice.application.features.user_connection.queries.get_connections.UserConnectionResult;
import iuh.fit.userservice.domain.entities.UserConnection;
import iuh.fit.userservice.domain.enums.ConnectionStatus;
import iuh.fit.userservice.domain.enums.ConnectionType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

import java.util.UUID;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserConnectionApplicationMapper {

    @Mapping(target = "requesterId", source = "requesterId")
    @Mapping(target = "targetId", source = "targetId")
    @Mapping(target = "type", source = "type")
    @Mapping(target = "status", source = "status")
    UserConnection toEntity(UUID requesterId, UUID targetId, ConnectionType type, ConnectionStatus status);

    UserConnectionResult toResult(UserConnection connection);

    @Mapping(target = "page", source = "number")
    @Mapping(target = "size", source = "size")
    @Mapping(target = "totalElements", source = "totalElements")
    @Mapping(target = "totalPages", source = "totalPages")
    @Mapping(target = "last", source = "last")
    @Mapping(target = "content", source = "content")
    PagedResponse<UserConnectionResult> toPagedResponse(Page<UserConnection> page);
}
