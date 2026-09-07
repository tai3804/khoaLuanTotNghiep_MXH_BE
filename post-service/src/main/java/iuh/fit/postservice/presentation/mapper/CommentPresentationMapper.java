package iuh.fit.postservice.presentation.mapper;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.postservice.application.features.comment.commands.create_comment.CreateCommentCommand;
import iuh.fit.postservice.application.features.comment.commands.create_comment.CreateCommentResult;
import iuh.fit.postservice.presentation.dto.request.CreateCommentRequest;
import iuh.fit.postservice.presentation.dto.response.CommentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CommentPresentationMapper {

    @Mapping(target = "postId", source = "postId")
    @Mapping(target = "authorId", source = "authorId")
    @Mapping(target = "file", source = "file")
    CreateCommentCommand toCreateCommand(CreateCommentRequest request, MultipartFile file, UUID postId, UUID authorId);

    CommentResponse toResponse(CreateCommentResult result);

    PagedResponse<CommentResponse> toPagedResponse(PagedResponse<CreateCommentResult> result);
}
