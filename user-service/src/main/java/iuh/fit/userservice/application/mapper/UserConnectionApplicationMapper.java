package iuh.fit.userservice.application.mapper;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.userservice.application.features.user_connection.queries.get_connections.UserConnectionResult;
import iuh.fit.userservice.application.features.user_connection.queries.get_suggestions.FriendSuggestionResult;
import iuh.fit.userservice.domain.entities.UserConnection;
import iuh.fit.userservice.domain.entities.UserProfile;
import iuh.fit.userservice.domain.enums.ConnectionStatus;
import iuh.fit.userservice.domain.enums.ConnectionType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserConnectionApplicationMapper {

    @Mapping(target = "requesterId", source = "requesterId")
    @Mapping(target = "targetId", source = "targetId")
    @Mapping(target = "type", source = "type")
    @Mapping(target = "status", source = "status")
    UserConnection toEntity(UUID requesterId, UUID targetId, ConnectionType type, ConnectionStatus status);

    UserConnectionResult toResult(UserConnection connection);

    default UserConnectionResult toResult(UUID userId, UserProfile profile) {
        if (profile == null) {
            return UserConnectionResult.builder()
                    .userId(userId)
                    .build();
        }
        String fullName = Stream.of(profile.getLastName(), profile.getMiddleName(), profile.getFirstName())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" "));
        return UserConnectionResult.builder()
                .userId(userId)
                .fullName(fullName)
                .avatarUrl(profile.getAvatarUrl())
                .isOnline(Boolean.TRUE.equals(profile.getIsOnline()))
                .build();
    }

    default FriendSuggestionResult toSuggestionResult(UUID userId, UserProfile profile, long mutualCount) {
        if (profile == null) {
            return FriendSuggestionResult.builder()
                    .userId(userId)
                    .mutualFriendsCount(mutualCount)
                    .build();
        }
        String fullName = Stream.of(profile.getLastName(), profile.getMiddleName(), profile.getFirstName())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" "));
        return FriendSuggestionResult.builder()
                .userId(userId)
                .fullName(fullName)
                .avatarUrl(profile.getAvatarUrl())
                .mutualFriendsCount(mutualCount)
                .build();
    }

    default PagedResponse<UserConnectionResult> toPagedResponse(Page<UserConnection> page) {
        if (page == null) return null;
        java.util.List<UserConnectionResult> content = page.getContent() == null ? java.util.Collections.emptyList() :
                page.getContent().stream().map(this::toResult).toList();
        return PagedResponse.<UserConnectionResult>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
