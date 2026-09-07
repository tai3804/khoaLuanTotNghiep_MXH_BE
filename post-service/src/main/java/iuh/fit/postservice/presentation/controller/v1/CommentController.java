package iuh.fit.postservice.presentation.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.application.exception.ErrorCode;
import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.postservice.application.features.comment.commands.create_comment.CreateCommentCommand;
import iuh.fit.postservice.application.features.comment.commands.create_comment.CreateCommentCommandHandler;
import iuh.fit.postservice.application.features.comment.commands.create_comment.CreateCommentResult;
import iuh.fit.postservice.application.features.comment.commands.delete_comment.DeleteCommentCommand;
import iuh.fit.postservice.application.features.comment.commands.delete_comment.DeleteCommentCommandHandler;
import iuh.fit.postservice.application.features.comment.queries.get_all_comments.GetAllCommentsQuery;
import iuh.fit.postservice.application.features.comment.queries.get_all_comments.GetAllCommentsQueryHandler;
import iuh.fit.postservice.application.features.comment.queries.get_post_comments.GetPostCommentsQuery;
import iuh.fit.postservice.application.features.comment.queries.get_post_comments.GetPostCommentsQueryHandler;
import iuh.fit.postservice.presentation.constants.ApiConstants;
import iuh.fit.postservice.presentation.constants.MessageConstants;
import iuh.fit.postservice.presentation.dto.request.CreateCommentRequest;
import iuh.fit.postservice.presentation.dto.response.CommentResponse;
import iuh.fit.postservice.presentation.mapper.CommentPresentationMapper;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.POST_API + "/{postId}/comments")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Post Comments", description = "APIs for adding, deleting, and viewing comments on posts")
@SecurityRequirement(name = "bearerAuth")
public class CommentController {

    CreateCommentCommandHandler createCommentCommandHandler;
    DeleteCommentCommandHandler deleteCommentCommandHandler;
    GetPostCommentsQueryHandler getPostCommentsQueryHandler;
    GetAllCommentsQueryHandler getAllCommentsQueryHandler;
    CommentPresentationMapper commentPresentationMapper;
    JwtUtil jwtUtil;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Create comment on post (Form Data)",
            description = "Adds a comment or reply to a post with optional media file upload via form-data",
            requestBody = @RequestBody(
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schemaProperties = {
                                    @SchemaProperty(name = "parentCommentId", schema = @Schema(type = "string", format = "uuid")),
                                    @SchemaProperty(name = "content", schema = @Schema(type = "string")),
                                    @SchemaProperty(name = "file", schema = @Schema(type = "string", format = "binary"))
                            }
                    )
            )
    )
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @PathVariable UUID postId,
            @Valid @ModelAttribute CreateCommentRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        UUID currentUserId = getCurrentUserId();
        MultipartFile validFile = (file != null && !file.isEmpty()) ? file : null;

        CreateCommentCommand command = commentPresentationMapper.toCreateCommand(request, validFile, postId, currentUserId);
        CreateCommentResult result = createCommentCommandHandler.handle(command);
        CommentResponse response = commentPresentationMapper.toResponse(result);
        return ResponseEntity.ok(ApiResponse.success(response, MessageConstants.COMMENT_CREATED_SUCCESSFULLY));
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "Delete comment", description = "Deletes a comment and removes associated media file from S3")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable UUID postId,
            @PathVariable UUID commentId) {
        UUID currentUserId = getCurrentUserId();
        DeleteCommentCommand command = DeleteCommentCommand.builder().commentId(commentId).userId(currentUserId).build();
        deleteCommentCommandHandler.handle(command);
        return ResponseEntity.ok(ApiResponse.success(null, MessageConstants.COMMENT_DELETED_SUCCESSFULLY));
    }

    @GetMapping
    @Operation(summary = "Get post comments", description = "Retrieves paginated list of top-level comments or nested replies for a post")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> getPostComments(
            @PathVariable UUID postId,
            @RequestParam(required = false) UUID parentCommentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        GetPostCommentsQuery query = GetPostCommentsQuery.builder()
                .postId(postId)
                .parentCommentId(parentCommentId)
                .page(page)
                .size(size)
                .build();
        PagedResponse<CreateCommentResult> result = getPostCommentsQueryHandler.handle(query);
        PagedResponse<CommentResponse> pagedResponse = commentPresentationMapper.toPagedResponse(result);
        return ResponseEntity.ok(ApiResponse.paged(pagedResponse, MessageConstants.COMMENTS_RETRIEVED_SUCCESSFULLY));
    }

    @GetMapping("/all")
    @Operation(summary = "Get all comments with BaseFilter", description = "Retrieves paginated list of comments using BaseFilter")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> getAllComments(
            @PathVariable UUID postId,
            @ParameterObject @Valid @ModelAttribute BaseFilter filter) {
        if (filter.getFilters() != null) {
            filter.getFilters().put("postId", postId);
        }
        GetAllCommentsQuery query = GetAllCommentsQuery.builder().filter(filter).build();
        PagedResponse<CreateCommentResult> result = getAllCommentsQueryHandler.handle(query);
        PagedResponse<CommentResponse> pagedResponse = commentPresentationMapper.toPagedResponse(result);
        return ResponseEntity.ok(ApiResponse.paged(pagedResponse, MessageConstants.COMMENTS_RETRIEVED_SUCCESSFULLY));
    }

    private UUID getCurrentUserId() {
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return UUID.fromString(userIdStr);
    }
}
