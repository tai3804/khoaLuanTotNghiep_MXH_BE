package iuh.fit.postservice.application.features.reaction.queries.get_post_reactions;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.application.mapper.ReactionFeatureMapper;
import iuh.fit.postservice.application.service.PostVisibilityService;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.entities.Reaction;
import iuh.fit.postservice.domain.enums.PostStatus;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.ReactionRepository;
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
import java.util.Objects;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetPostReactionsQueryHandler {

    ReactionRepository reactionRepository;
    PostRepository postRepository;
    PostVisibilityService postVisibilityService;
    JwtUtil jwtUtil;
    ReactionFeatureMapper reactionFeatureMapper;

    @Transactional(readOnly = true)
    public PagedResponse<GetPostReactionsResult> handle(GetPostReactionsQuery query) {
        Post post = postRepository.findByIdAndDeletedFalse(query.getPostId())
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));

        boolean isStaff = isStaffUser();
        boolean isAuthor = query.getViewerId() != null && Objects.equals(query.getViewerId(), post.getAuthorId());
        if (!isStaff && !isAuthor) {
            if (post.isArchived()
                    || post.getStatus() != PostStatus.PUBLISHED
                    || !postVisibilityService.canView(post, query.getViewerId())) {
                throw new BusinessException(PostServiceErrorCode.POST_NOT_FOUND);
            }
        }

        Pageable pageable = PageRequest.of(
                Math.max(0, query.getPage()),
                query.getSize() > 0 ? Math.min(query.getSize(), 100) : 20,
                Sort.by("createdAt").descending()
        );
        Page<Reaction> reactionsPage = reactionRepository.findByPostId(query.getPostId(), pageable);

        List<GetPostReactionsResult> content = reactionsPage.getContent().stream()
                .map(reactionFeatureMapper::toGetReactionsResult)
                .toList();

        return reactionFeatureMapper.toPagedResponse(reactionsPage, content);
    }

    private boolean isStaffUser() {
        try {
            List<String> roles = jwtUtil.getCurrentUserRoles();
            return roles != null && roles.stream().anyMatch(r ->
                    "ROLE_ADMIN".equalsIgnoreCase(r) ||
                    "ADMIN".equalsIgnoreCase(r) ||
                    "ROLE_MODERATOR".equalsIgnoreCase(r) ||
                    "MODERATOR".equalsIgnoreCase(r)
            );
        } catch (Exception ignored) {
            return false;
        }
    }
}
