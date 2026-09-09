package iuh.fit.postservice.application.features.saved_post.queries.get_saved_posts;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.postservice.application.features.post.queries.get_post_detail.GetPostDetailResult;
import iuh.fit.postservice.application.mapper.PostFeatureMapper;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.entities.PostMedia;
import iuh.fit.postservice.domain.entities.SavedPost;
import iuh.fit.postservice.infrastructure.persistence.repository.PostMediaRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.SavedPostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetSavedPostsHandler {

    SavedPostRepository savedPostRepository;
    PostRepository postRepository;
    PostMediaRepository postMediaRepository;
    PostFeatureMapper postFeatureMapper;

    @Transactional(readOnly = true)
    public PagedResponse<GetPostDetailResult> handle(GetSavedPostsQuery query) {
        int page = query.getFilter() != null ? query.getFilter().getPage() : 0;
        int size = query.getFilter() != null ? query.getFilter().getSize() : 20;
        Pageable pageable = PageRequest.of(page, size);

        Page<SavedPost> savedPostPage;
        if (query.getCollectionName() != null && !query.getCollectionName().trim().isEmpty()) {
            savedPostPage = savedPostRepository.findByUserIdAndCollectionNameOrderByCreatedAtDesc(
                    query.getUserId(), query.getCollectionName(), pageable
            );
        } else {
            savedPostPage = savedPostRepository.findByUserIdOrderByCreatedAtDesc(query.getUserId(), pageable);
        }

        List<UUID> postIds = savedPostPage.getContent().stream().map(SavedPost::getPostId).toList();
        if (postIds.isEmpty()) {
            return PagedResponse.<GetPostDetailResult>builder()
                    .content(List.of())
                    .page(savedPostPage.getNumber())
                    .size(savedPostPage.getSize())
                    .totalElements(0)
                    .totalPages(0)
                    .last(true)
                    .build();
        }

        Map<UUID, Post> postMap = postRepository.findAllById(postIds).stream()
                .filter(p -> !p.isDeleted())
                .collect(Collectors.toMap(Post::getId, p -> p));

        List<PostMedia> mediaList = postMediaRepository.findByPostIdInOrderBySortOrderAsc(postIds);
        Map<UUID, List<PostMedia>> mediaMap = mediaList.stream()
                .collect(Collectors.groupingBy(PostMedia::getPostId));

        List<GetPostDetailResult> detailResults = new ArrayList<>();
        for (SavedPost savedPost : savedPostPage.getContent()) {
            Post post = postMap.get(savedPost.getPostId());
            if (post != null) {
                List<PostMedia> pMedia = mediaMap.getOrDefault(post.getId(), List.of());
                detailResults.add(postFeatureMapper.toGetDetailResult(post, pMedia));
            }
        }

        return PagedResponse.<GetPostDetailResult>builder()
                .content(detailResults)
                .page(savedPostPage.getNumber())
                .size(savedPostPage.getSize())
                .totalElements(savedPostPage.getTotalElements())
                .totalPages(savedPostPage.getTotalPages())
                .last(savedPostPage.isLast())
                .build();
    }
}
