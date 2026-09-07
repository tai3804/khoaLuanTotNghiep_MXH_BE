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

        List<CreateCommentResult> content = commentsPage.getContent().stream()
                .map(commentFeatureMapper::toCreateResult)
                .toList();

        return commentFeatureMapper.toPagedResponse(commentsPage, content);
    }
}
