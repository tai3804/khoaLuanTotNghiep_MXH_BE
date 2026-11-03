package iuh.fit.postservice.application.features.post.queries.get_user_posts;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.postservice.application.features.post.queries.get_post_detail.GetPostDetailResult;
import iuh.fit.postservice.application.mapper.PostFeatureMapper;
import iuh.fit.postservice.application.service.PostVisibilityService;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.entities.PostMedia;
import iuh.fit.postservice.domain.enums.PostStatus;
import iuh.fit.postservice.infrastructure.persistence.repository.PostMediaRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetUserPostsQueryHandler {

    PostRepository postRepository;
    PostMediaRepository postMediaRepository;
    PostFeatureMapper postFeatureMapper;
    PostVisibilityService postVisibilityService;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "post-user-feed-v2", key = "#query.viewerId + ':' + #query.userId + ':' + #query.page + ':' + #query.size")
    public PagedResponse<GetPostDetailResult> handle(GetUserPostsQuery query) {
        Pageable pageable = PageRequest.of(
                Math.max(0, query.getPage()),
                query.getSize() > 0 ? Math.min(query.getSize(), 50) : 10,
                Sort.by(Sort.Order.desc("isPinned"), Sort.Order.desc("createdAt"))
        );
        Page<Post> postsPage = postRepository.findByAuthorIdAndStatusAndDeletedFalseAndIsArchivedFalse(
                query.getUserId(), PostStatus.PUBLISHED, pageable
        );

        Set<java.util.UUID> visiblePostIds = postVisibilityService.visiblePostIds(postsPage.getContent(), query.getViewerId(), false);
        List<Post> visiblePosts = postsPage.getContent().stream()
                .filter(post -> visiblePostIds.contains(post.getId()))
                .toList();

        List<java.util.UUID> postIds = visiblePosts.stream().map(Post::getId).toList();
        java.util.Map<java.util.UUID, List<PostMedia>> mediaMap = postIds.isEmpty() ? java.util.Collections.emptyMap()
                : postMediaRepository.findByPostIdInOrderBySortOrderAsc(postIds).stream()
                        .collect(java.util.stream.Collectors.groupingBy(PostMedia::getPostId));

        List<GetPostDetailResult> content = visiblePosts.stream().map(post -> {
            List<PostMedia> mediaList = mediaMap.getOrDefault(post.getId(), java.util.Collections.emptyList());
            GetPostDetailResult res = postFeatureMapper.toGetDetailResult(post, mediaList);
            res.setPoll(iuh.fit.postservice.application.util.PostPollHelper.toResponse(post.getPollData(), query.getViewerId()));
            return res;
        }).toList();

        return postFeatureMapper.toPagedResponse(postsPage, content);
    }
}
