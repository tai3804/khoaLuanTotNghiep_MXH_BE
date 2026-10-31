package iuh.fit.postservice.application.features.comment.queries.get_post_comments;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.application.features.comment.commands.create_comment.CreateCommentResult;
import iuh.fit.postservice.application.mapper.CommentFeatureMapper;
import iuh.fit.postservice.application.service.PostVisibilityService;
import iuh.fit.postservice.domain.entities.Comment;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.enums.PostStatus;
import iuh.fit.postservice.infrastructure.persistence.repository.CommentRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
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
public class GetPostCommentsQueryHandler {

    CommentRepository commentRepository;
    PostRepository postRepository;
    PostVisibilityService postVisibilityService;
    JwtUtil jwtUtil;
    CommentFeatureMapper commentFeatureMapper;

    @Transactional(readOnly = true)
    public PagedResponse<CreateCommentResult> handle(GetPostCommentsQuery query) {
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
                query.getSize() > 0 ? Math.min(query.getSize(), 100) : 10,
                Sort.by("createdAt").ascending()
        );
        Page<Comment> commentsPage;

        if (query.getParentCommentId() != null) {
            Comment parent = commentRepository.findByIdAndDeletedFalse(query.getParentCommentId())
                    .orElseThrow(() -> new BusinessException(PostServiceErrorCode.COMMENT_NOT_FOUND));
            if (!parent.getPostId().equals(query.getPostId())) {
                throw new BusinessException(PostServiceErrorCode.COMMENT_NOT_FOUND);
            }
            commentsPage = commentRepository.findByParentCommentIdAndDeletedFalse(query.getParentCommentId(), pageable);
        } else {
            commentsPage = commentRepository.findByPostIdAndParentCommentIdIsNullAndDeletedFalse(query.getPostId(), pageable);
        }

        java.util.Map<java.util.UUID, Long> authorCounts = commentsPage.getContent().stream()
                .filter(c -> c.getAuthorId() != null)
                .collect(java.util.stream.Collectors.groupingBy(Comment::getAuthorId, java.util.stream.Collectors.counting()));

        List<CreateCommentResult> content = commentsPage.getContent().stream()
                .map(comment -> {
                    CreateCommentResult res = commentFeatureMapper.toCreateResult(comment);
                    if (res != null) {
                        boolean topFan = (comment.getLikeCount() >= 2) || (authorCounts.getOrDefault(comment.getAuthorId(), 0L) >= 2);
                        res.setTopFan(topFan);
                    }
                    return res;
                })
                .toList();

        return commentFeatureMapper.toPagedResponse(commentsPage, content);
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
