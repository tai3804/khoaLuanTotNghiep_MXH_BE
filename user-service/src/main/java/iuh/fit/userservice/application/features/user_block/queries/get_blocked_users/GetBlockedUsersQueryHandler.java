package iuh.fit.userservice.application.features.user_block.queries.get_blocked_users;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import iuh.fit.commonframework.infrastructure.filter.SortDirection;
import iuh.fit.userservice.domain.entities.UserBlock;
import iuh.fit.userservice.domain.entities.UserProfile;
import iuh.fit.userservice.domain.repository.UserBlockRepository;
import iuh.fit.userservice.domain.repository.UserProfileRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetBlockedUsersQueryHandler {

    UserBlockRepository userBlockRepository;
    UserProfileRepository userProfileRepository;

    @Transactional(readOnly = true)
    public PagedResponse<UserBlockResult> handle(GetBlockedUsersQuery query) {
        BaseFilter filter = query.getFilter() != null ? query.getFilter() : new BaseFilter();
        int pageIndex = Math.max(0, filter.getPage() - 1);
        int pageSize = filter.getSize() > 0 ? Math.min(filter.getSize(), 100) : 10;
        String sortBy = (filter.getSortBy() != null && !filter.getSortBy().isBlank()) ? filter.getSortBy() : "createdAt";
        Sort.Direction direction = filter.getSortDirection() == SortDirection.DESC ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(direction, sortBy));

        Page<UserBlock> blockPage = userBlockRepository.findByBlockerId(query.getBlockerId(), pageable);

        List<UUID> blockedUserIds = blockPage.getContent().stream()
                .map(UserBlock::getBlockedId)
                .toList();

        Map<UUID, UserProfile> profileMap = userProfileRepository.findByUserIdIn(blockedUserIds).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, p -> p, (p1, p2) -> p1));

        List<UserBlockResult> results = blockPage.getContent().stream()
                .map(block -> {
                    UserProfile profile = profileMap.get(block.getBlockedId());
                    return UserBlockResult.builder()
                            .id(block.getId())
                            .blockerId(block.getBlockerId())
                            .blockedId(block.getBlockedId())
                            .blockedFirstName(profile != null ? profile.getFirstName() : "")
                            .blockedLastName(profile != null ? profile.getLastName() : "")
                            .blockedAvatarUrl(profile != null ? profile.getAvatarUrl() : null)
                            .reason(block.getReason())
                            .createdAt(block.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        return PagedResponse.<UserBlockResult>builder()
                .content(results)
                .page(blockPage.getNumber())
                .size(blockPage.getSize())
                .totalElements(blockPage.getTotalElements())
                .totalPages(blockPage.getTotalPages())
                .last(blockPage.isLast())
                .build();
    }
}
