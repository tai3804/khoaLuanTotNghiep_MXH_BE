package iuh.fit.postservice.presentation.mapper;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.postservice.application.features.post.commands.create_post.CreatePostCommand;
import iuh.fit.postservice.application.features.post.commands.create_post.CreatePostResult;
import iuh.fit.postservice.application.features.post.commands.share_post.SharePostCommand;
import iuh.fit.postservice.application.features.post.commands.share_post.SharePostResult;
import iuh.fit.postservice.application.features.post.commands.update_post.UpdatePostCommand;
import iuh.fit.postservice.application.features.post.commands.update_post.UpdatePostResult;
import iuh.fit.postservice.application.features.post.queries.get_post_detail.GetPostDetailResult;
import iuh.fit.postservice.presentation.dto.request.CreatePostRequest;
import iuh.fit.postservice.presentation.dto.request.SharePostRequest;
import iuh.fit.postservice.presentation.dto.request.UpdatePostRequest;
import iuh.fit.postservice.presentation.dto.response.PostResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PostPresentationMapper {

    @Mapping(target = "authorId", source = "authorId")
    @Mapping(target = "files", source = "files")
    CreatePostCommand toCreateCommand(CreatePostRequest request, List<MultipartFile> files, UUID authorId);

    @Mapping(target = "postId", source = "postId")
    @Mapping(target = "userId", source = "userId")
    UpdatePostCommand toUpdateCommand(UpdatePostRequest request, UUID postId, UUID userId);

    @Mapping(target = "originalPostId", source = "originalPostId")
    @Mapping(target = "userId", source = "userId")
    SharePostCommand toShareCommand(SharePostRequest request, UUID originalPostId, UUID userId);

    PostResponse toResponse(CreatePostResult result);
    PostResponse toResponse(UpdatePostResult result);
    PostResponse toResponse(GetPostDetailResult result);
    PostResponse toResponse(SharePostResult result);

    default PagedResponse<PostResponse> toPagedResponse(PagedResponse<GetPostDetailResult> result) {
        if (result == null) return null;
        List<PostResponse> list = result.getContent() == null ? java.util.Collections.emptyList() :
                result.getContent().stream().map(this::toResponse).toList();
        return PagedResponse.<PostResponse>builder()
                .content(list)
                .page(result.getPage())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build();
    }
}
