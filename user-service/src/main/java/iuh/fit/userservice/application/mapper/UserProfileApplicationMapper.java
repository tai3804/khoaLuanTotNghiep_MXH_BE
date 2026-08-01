package iuh.fit.userservice.application.mapper;

import iuh.fit.userservice.application.features.user_profile.commands.update_user_profile.UpdateUserProfileCommand;
import iuh.fit.userservice.application.features.user_profile.commands.update_user_profile.UpdateUserProfileResult;
import iuh.fit.userservice.application.features.user_profile.queries.get_user_profile.GetUserProfileResult;
import iuh.fit.userservice.domain.entities.UserProfile;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserProfileApplicationMapper {

    GetUserProfileResult toQueryResult(UserProfile userProfile);

    UpdateUserProfileResult toUpdateResult(UserProfile userProfile);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromCommand(UpdateUserProfileCommand command, @MappingTarget UserProfile userProfile);
}
