package iuh.fit.postservice.application.mapper;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.postservice.application.features.comment.commands.create_comment.CreateCommentCommand;
import iuh.fit.postservice.application.features.comment.commands.create_comment.CreateCommentResult;
import iuh.fit.postservice.domain.entities.Comment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CommentFeatureMapper {

    Comment toEntity(CreateCommentCommand command, String mediaUrl, String mediaKey);

    CreateCommentResult toCreateResult(Comment comment);

    default PagedResponse<CreateCommentResult> toPagedResponse(Page<Comment> page, List<CreateCommentResult> content) {
        if (page == null) return null;
        return PagedResponse.<CreateCommentResult>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
