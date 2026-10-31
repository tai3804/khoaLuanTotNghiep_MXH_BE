package iuh.fit.postservice.application.features.post.queries.get_post_detail;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.application.mapper.PostFeatureMapper;
import iuh.fit.postservice.application.service.PostVisibilityService;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.entities.PostMedia;
import iuh.fit.postservice.domain.enums.PostStatus;
import iuh.fit.postservice.infrastructure.client.user.UserConnectionClient;
import iuh.fit.postservice.infrastructure.persistence.repository.PostMediaRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetPostDetailQueryHandler {

    PostRepository postRepository;
    PostMediaRepository postMediaRepository;
    PostFeatureMapper postFeatureMapper;
    PostVisibilityService postVisibilityService;
    JwtUtil jwtUtil;
    UserConnectionClient userConnectionClient;

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = "post-detail-v2",
            key = "#query.viewerId + ':' + #query.postId",
            condition = "T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication() == null || !T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication().getAuthorities().toString().contains('INTERNAL')"
    )
    public GetPostDetailResult handle(GetPostDetailQuery query) {
        boolean isPrivileged = hasPrivilegedAuthority();

        Post post = (isPrivileged ? postRepository.findById(query.getPostId()) : postRepository.findByIdAndDeletedFalse(query.getPostId()))
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));

        boolean isAuthor = query.getViewerId() != null && Objects.equals(query.getViewerId(), post.getAuthorId());

        if (post.isArchived() && !isPrivileged && !isAuthor) {
            throw new BusinessException(PostServiceErrorCode.POST_NOT_FOUND);
        }

        // Unpublished posts must never be exposed merely by guessing their ID.
        // Their author may inspect them, and a group admin/moderator may inspect
        // a pending group post in order to review it.
        if (post.getStatus() != PostStatus.PUBLISHED && !isPrivileged && !isAuthor
                && !(post.getStatus() == PostStatus.PENDING_APPROVAL && query.getViewerId() != null && canModerateGroup(post.getGroupId()))) {
            throw new BusinessException(PostServiceErrorCode.POST_NOT_FOUND);
        }

        if (!isPrivileged && !postVisibilityService.canView(post, query.getViewerId())) {
            // Do not disclose whether a private/friends-only post exists.
            throw new BusinessException(PostServiceErrorCode.POST_NOT_FOUND);
        }

        List<PostMedia> mediaList = postMediaRepository.findByPostIdOrderBySortOrderAsc(post.getId());

        GetPostDetailResult result = postFeatureMapper.toGetDetailResult(post, mediaList);
        result.setPoll(iuh.fit.postservice.application.util.PostPollHelper.toResponse(post.getPollData(), query.getViewerId()));
        return result;
    }

    private boolean hasPrivilegedAuthority() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getAuthorities() != null) {
                boolean fromAuthorities = auth.getAuthorities().stream().anyMatch(a -> {
                    String role = a.getAuthority();
                    return "ROLE_INTERNAL".equalsIgnoreCase(role)
                            || "INTERNAL".equalsIgnoreCase(role)
                            || "ROLE_ADMIN".equalsIgnoreCase(role)
                            || "ADMIN".equalsIgnoreCase(role)
                            || "ROLE_MODERATOR".equalsIgnoreCase(role)
                            || "MODERATOR".equalsIgnoreCase(role);
                });
                if (fromAuthorities) {
                    return true;
                }
            }
            List<String> roles = jwtUtil.getCurrentUserRoles();
            if (roles != null) {
                return roles.stream().anyMatch(r ->
                        "ROLE_MODERATOR".equalsIgnoreCase(r) ||
                        "MODERATOR".equalsIgnoreCase(r) ||
                        "ROLE_ADMIN".equalsIgnoreCase(r) ||
                        "ADMIN".equalsIgnoreCase(r)
                );
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean canModerateGroup(java.util.UUID groupId) {
        if (groupId == null) return false;
        try {
            var response = userConnectionClient.getGroupFeedVisibility(List.of(groupId));
            if (response == null || response.getData() == null) return false;
            return response.getData().stream().anyMatch(group -> groupId.equals(group.groupId()) && group.member()
                    && ("ADMIN".equals(group.role()) || "MODERATOR".equals(group.role())));
        } catch (Exception ignored) {
            return false;
        }
    }
}
