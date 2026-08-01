package iuh.fit.userservice.application.mapper;

import iuh.fit.userservice.domain.entities.UserProfile;
import iuh.fit.userservice.domain.enums.Gender;
import iuh.fit.userservice.presentation.dto.event.UserRegisteredEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserProfileMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "gender", source = "gender", qualifiedByName = "mapGender")
    @Mapping(target = "followerCount", constant = "0L")
    @Mapping(target = "followingCount", constant = "0L")
    @Mapping(target = "friendCount", constant = "0L")
    @Mapping(target = "avatarUrl", ignore = true)
    @Mapping(target = "coverUrl", ignore = true)
    @Mapping(target = "bio", ignore = true)
    @Mapping(target = "location", ignore = true)
    @Mapping(target = "website", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    UserProfile toEntity(UserRegisteredEvent event);

    @Named("mapGender")
    default Gender mapGender(String gender) {
        if (gender == null || gender.isBlank()) {
            return null;
        }
        try {
            return Gender.valueOf(gender.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
