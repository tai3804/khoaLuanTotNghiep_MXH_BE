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

    @Mapping(target = "content", source = "content")
    @Mapping(target = "page", source = "page.number")
    @Mapping(target = "size", source = "page.size")
    @Mapping(target = "totalElements", source = "page.totalElements")
    @Mapping(target = "totalPages", source = "page.totalPages")
    @Mapping(target = "last", source = "page.last")
    PagedResponse<CreateCommentResult> toPagedResponse(Page<Comment> page, List<CreateCommentResult> content);
}
