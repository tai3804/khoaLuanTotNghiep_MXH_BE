package iuh.fit.userservice.application.mapper;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.userservice.application.features.user_profile.commands.update_user_profile.UpdateUserProfileCommand;
import iuh.fit.userservice.application.features.user_profile.commands.update_user_profile.UpdateUserProfileResult;
import iuh.fit.userservice.application.features.user_profile.queries.get_user_profile.GetUserProfileResult;
import iuh.fit.userservice.domain.entities.UserProfile;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

import iuh.fit.commonframework.event.UserAvatarUpdatedEvent;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserProfileApplicationMapper {

    GetUserProfileResult toQueryResult(UserProfile userProfile);

    UpdateUserProfileResult toUpdateResult(UserProfile userProfile);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromCommand(UpdateUserProfileCommand command, @MappingTarget UserProfile userProfile);

    default UserAvatarUpdatedEvent toAvatarUpdatedEvent(UUID userId, String oldAvatarUrl, String newAvatarUrl, String oldCoverUrl, String newCoverUrl) {
        return UserAvatarUpdatedEvent.builder()
                .userId(userId)
                .oldAvatarUrl(oldAvatarUrl)
                .newAvatarUrl(newAvatarUrl)
                .oldCoverUrl(oldCoverUrl)
                .newCoverUrl(newCoverUrl)
                .build();
    }

    default PagedResponse<GetUserProfileResult> toPagedResult(Page<UserProfile> page) {
        if (page == null) return null;
        List<GetUserProfileResult> content = page.getContent() == null ? Collections.emptyList() :
                page.getContent().stream().map(this::toQueryResult).toList();
        return PagedResponse.<GetUserProfileResult>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
