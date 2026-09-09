package iuh.fit.userservice.presentation.mapper;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.userservice.application.features.user_profile.commands.update_user_profile.UpdateUserProfileCommand;
import iuh.fit.userservice.application.features.user_profile.commands.update_user_profile.UpdateUserProfileResult;
import iuh.fit.userservice.application.features.user_profile.queries.get_user_profile.GetUserProfileResult;
import iuh.fit.userservice.presentation.dto.request.UpdateUserProfileRequest;
import iuh.fit.userservice.presentation.dto.response.UserProfileResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserProfilePresentationMapper {

    UpdateUserProfileCommand toCommand(UpdateUserProfileRequest request);

    UserProfileResponse toResponse(GetUserProfileResult result);

    UserProfileResponse toResponse(UpdateUserProfileResult result);

    default PagedResponse<UserProfileResponse> toPagedResponse(PagedResponse<GetUserProfileResult> pagedResult) {
        if (pagedResult == null) return null;
        List<UserProfileResponse> content = pagedResult.getContent() == null ? Collections.emptyList() :
                pagedResult.getContent().stream().map(this::toResponse).toList();
        return PagedResponse.<UserProfileResponse>builder()
                .content(content)
                .page(pagedResult.getPage())
                .size(pagedResult.getSize())
                .totalElements(pagedResult.getTotalElements())
                .totalPages(pagedResult.getTotalPages())
                .last(pagedResult.isLast())
                .build();
    }
}
