package iuh.fit.postservice.application.features.comment.queries.get_post_comments;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.postservice.application.features.comment.commands.create_comment.CreateCommentResult;
import iuh.fit.postservice.application.mapper.CommentFeatureMapper;
import iuh.fit.postservice.domain.entities.Comment;
import iuh.fit.postservice.infrastructure.persistence.repository.CommentRepository;
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

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetPostCommentsQueryHandler {

    CommentRepository commentRepository;
    CommentFeatureMapper commentFeatureMapper;

    @Transactional(readOnly = true)
    public PagedResponse<CreateCommentResult> handle(GetPostCommentsQuery query) {
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), Sort.by("createdAt").ascending());
        Page<Comment> commentsPage;

        if (query.getParentCommentId() != null) {
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
}
