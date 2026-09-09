package iuh.fit.userservice.presentation.mapper;

import iuh.fit.userservice.application.features.user_presence.queries.get_user_presence.UserPresenceResult;
import iuh.fit.userservice.presentation.dto.response.UserPresenceResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserPresencePresentationMapper {

    UserPresenceResponse toResponse(UserPresenceResult result);

    List<UserPresenceResponse> toResponseList(List<UserPresenceResult> results);
}
