package iuh.fit.userservice.presentation.mapper;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.userservice.application.features.user_block.queries.check_user_block.CheckUserBlockQueryResult;
import iuh.fit.userservice.application.features.user_block.queries.get_blocked_users.UserBlockResult;
import iuh.fit.userservice.presentation.dto.response.CheckBlockResponse;
import iuh.fit.userservice.presentation.dto.response.UserBlockResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserBlockPresentationMapper {

    UserBlockResponse toResponse(UserBlockResult result);

    CheckBlockResponse toResponse(CheckUserBlockQueryResult result);

    default PagedResponse<UserBlockResponse> toPagedResponse(PagedResponse<UserBlockResult> pagedResult) {
        if (pagedResult == null) return null;
        List<UserBlockResponse> content = pagedResult.getContent() == null ? java.util.Collections.emptyList() :
                pagedResult.getContent().stream().map(this::toResponse).toList();
        return PagedResponse.<UserBlockResponse>builder()
                .content(content)
                .page(pagedResult.getPage())
                .size(pagedResult.getSize())
                .totalElements(pagedResult.getTotalElements())
                .totalPages(pagedResult.getTotalPages())
                .last(pagedResult.isLast())
                .build();
    }
}
