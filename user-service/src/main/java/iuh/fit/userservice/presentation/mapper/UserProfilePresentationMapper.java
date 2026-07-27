package iuh.fit.userservice.presentation.mapper;

import iuh.fit.userservice.application.features.user_profile.commands.update_user_profile.UpdateUserProfileCommand;
import iuh.fit.userservice.application.features.user_profile.commands.update_user_profile.UpdateUserProfileResult;
import iuh.fit.userservice.application.features.user_profile.queries.get_user_profile.GetUserProfileResult;
import iuh.fit.userservice.presentation.dto.request.UpdateUserProfileRequest;
import iuh.fit.userservice.presentation.dto.response.UserProfileResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserProfilePresentationMapper {

    UpdateUserProfileCommand toCommand(UpdateUserProfileRequest request);

    UserProfileResponse toResponse(GetUserProfileResult result);

    UserProfileResponse toResponse(UpdateUserProfileResult result);
}
