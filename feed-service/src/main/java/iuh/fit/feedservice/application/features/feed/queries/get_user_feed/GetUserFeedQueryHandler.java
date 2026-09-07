package iuh.fit.feedservice.application.features.feed.queries.get_user_feed;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.feedservice.domain.entities.UserFeedItem;
import iuh.fit.feedservice.infrastructure.client.PostServiceClient;
import iuh.fit.feedservice.infrastructure.client.dto.PostResponseDto;
import iuh.fit.feedservice.infrastructure.persistence.repository.UserFeedItemRepository;
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

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetUserFeedQueryHandler {

    UserFeedItemRepository userFeedItemRepository;
    PostServiceClient postServiceClient;

    @Transactional(readOnly = true)
    public PagedResponse<GetUserFeedResult> handle(GetUserFeedQuery query) {
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize());
        Page<UserFeedItem> feedItemsPage = userFeedItemRepository.findByUserIdOrderByCreatedAtDesc(query.getUserId(), pageable);

        List<GetUserFeedResult> results = new ArrayList<>();
        for (UserFeedItem item : feedItemsPage.getContent()) {
            PostResponseDto postDetail = null;
            try {
                ApiResponse<PostResponseDto> response = postServiceClient.getPostById(item.getPostId());
                if (response != null && response.getData() != null) {
                    postDetail = response.getData();
                }
            } catch (Exception ignored) {
                // If post-service returns 404 or is unavailable, fallback gracefully
            }

            results.add(GetUserFeedResult.builder()
                    .feedItemId(item.getId())
                    .postId(item.getPostId())
                    .authorId(item.getAuthorId())
                    .postDetail(postDetail)
                    .createdAt(item.getCreatedAt())
                    .build());
        }

        return PagedResponse.<GetUserFeedResult>builder()
                .content(results)
                .page(feedItemsPage.getNumber())
                .size(feedItemsPage.getSize())
                .totalElements(feedItemsPage.getTotalElements())
                .totalPages(feedItemsPage.getTotalPages())
                .last(feedItemsPage.isLast())
                .build();
    }
}
