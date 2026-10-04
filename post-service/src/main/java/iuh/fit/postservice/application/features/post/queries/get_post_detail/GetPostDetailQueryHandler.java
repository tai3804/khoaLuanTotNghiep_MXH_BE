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
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    @Cacheable(cacheNames = "post-detail-v2", key = "#query.viewerId + ':' + #query.postId")
    public GetPostDetailResult handle(GetPostDetailQuery query) {
        boolean isStaff = false;
        try {
            List<String> roles = jwtUtil.getCurrentUserRoles();
            if (roles != null) {
                isStaff = roles.stream().anyMatch(r ->
                    r.equalsIgnoreCase("ROLE_MODERATOR") ||
                    r.equalsIgnoreCase("MODERATOR") ||
                    r.equalsIgnoreCase("ROLE_ADMIN") ||
                    r.equalsIgnoreCase("ADMIN")
                );
            }
        } catch (Exception ignored) {}

        Post post = (isStaff ? postRepository.findById(query.getPostId()) : postRepository.findByIdAndDeletedFalse(query.getPostId()))
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));

        // Unpublished posts must never be exposed merely by guessing their ID.
        // Their author may inspect them, and a group admin/moderator may inspect
        // a pending group post in order to review it.
        if (post.getStatus() != PostStatus.PUBLISHED && !isStaff && !query.getViewerId().equals(post.getAuthorId())
                && !(post.getStatus() == PostStatus.PENDING_APPROVAL && canModerateGroup(post.getGroupId()))) {
            throw new BusinessException(PostServiceErrorCode.POST_NOT_FOUND);
        }

        if (!isStaff && !postVisibilityService.canView(post, query.getViewerId())) {
            // Do not disclose whether a private/friends-only post exists.
            throw new BusinessException(PostServiceErrorCode.POST_NOT_FOUND);
        }

        List<PostMedia> mediaList = postMediaRepository.findByPostIdOrderBySortOrderAsc(post.getId());

        return postFeatureMapper.toGetDetailResult(post, mediaList);
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
