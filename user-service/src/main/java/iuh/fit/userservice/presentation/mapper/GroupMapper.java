package iuh.fit.userservice.presentation.mapper;

import iuh.fit.userservice.domain.entities.Group;
import iuh.fit.userservice.presentation.dto.request.CreateGroupRequest;
import iuh.fit.userservice.presentation.dto.response.GroupResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface GroupMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "coverUrl", ignore = true)
    @Mapping(target = "creatorId", ignore = true)
    @Mapping(target = "memberCount", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Group toEntity(CreateGroupRequest request);

    GroupResponse toResponse(Group group);
}

