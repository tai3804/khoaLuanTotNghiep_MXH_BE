package iuh.fit.postservice.application.mapper;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.postservice.application.features.post.commands.create_post.CreatePostCommand;
import iuh.fit.postservice.application.features.post.commands.create_post.CreatePostResult;
import iuh.fit.postservice.application.features.post.commands.share_post.SharePostCommand;
import iuh.fit.postservice.application.features.post.commands.share_post.SharePostResult;
import iuh.fit.postservice.application.features.post.commands.update_post.UpdatePostResult;
import iuh.fit.postservice.application.features.post.queries.get_post_detail.GetPostDetailResult;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.entities.PostMedia;
import iuh.fit.postservice.infrastructure.client.media.dto.MediaClientResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PostFeatureMapper {

    Post toEntity(CreatePostCommand command);

    @Mapping(target = "authorId", source = "command.userId")
    @Mapping(target = "content", source = "command.caption")
    Post toSharedPostEntity(SharePostCommand command);

    PostMedia toPostMedia(MediaClientResponse clientResp, UUID postId, int sortOrder);

    CreatePostResult toCreateResult(Post post, List<PostMedia> mediaList);

    UpdatePostResult toUpdateResult(Post post, List<PostMedia> mediaList);

    GetPostDetailResult toGetDetailResult(Post post, List<PostMedia> mediaList);

    SharePostResult toShareResult(Post post);

    default PagedResponse<GetPostDetailResult> toPagedResponse(Page<Post> page, List<GetPostDetailResult> content) {
        if (page == null) return null;
        return PagedResponse.<GetPostDetailResult>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
