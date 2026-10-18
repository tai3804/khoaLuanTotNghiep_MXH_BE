package iuh.fit.postservice.application.features.post.queries.get_groups_feed;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.application.features.post.queries.get_post_detail.GetPostDetailResult;
import iuh.fit.postservice.application.mapper.PostFeatureMapper;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.entities.PostMedia;
import iuh.fit.postservice.domain.enums.PostStatus;
import iuh.fit.postservice.infrastructure.client.user.UserConnectionClient;
import iuh.fit.postservice.infrastructure.persistence.repository.PostMediaRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bảng tin "Nhóm của bạn": bài viết mới trong mọi nhóm mà người xem là thành viên (giống trang Nhóm của Facebook).
 * sort=activity (mặc định): bài có hoạt động gần đây nhất lên đầu; sort=newest: bài đăng mới nhất lên đầu.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GetGroupsFeedQueryHandler {

    private static final int MAX_SIZE = 50;

    private final UserConnectionClient userConnectionClient;
    private final PostRepository postRepository;
    private final PostMediaRepository postMediaRepository;
    private final PostFeatureMapper postFeatureMapper;

    @Transactional(readOnly = true)
    public PagedResponse<GetPostDetailResult> handle(int page, int size, String sort) {
        List<UUID> groupIds = joinedGroupIds();
        int pageIndex = Math.max(page, 1) - 1;
        int pageSize = Math.min(Math.max(size, 1), MAX_SIZE);
        if (groupIds.isEmpty()) {
            return PagedResponse.<GetPostDetailResult>builder()
                    .content(List.of()).page(pageIndex).size(pageSize).totalElements(0).totalPages(0).last(true).build();
        }

        String sortField = "newest".equalsIgnoreCase(sort) ? "createdAt" : "lastActivityAt";
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Order.desc(sortField), Sort.Order.desc("id")));
        Page<Post> posts = postRepository.findByGroupIdInAndStatusAndDeletedFalseAndIsArchivedFalse(groupIds, PostStatus.PUBLISHED, pageable);

        Map<UUID, List<PostMedia>> mediaByPost = posts.isEmpty() ? Map.of()
                : postMediaRepository.findByPostIdInOrderBySortOrderAsc(posts.getContent().stream().map(Post::getId).toList()).stream()
                .collect(Collectors.groupingBy(PostMedia::getPostId));
        List<GetPostDetailResult> content = posts.getContent().stream()
                .map(post -> postFeatureMapper.toGetDetailResult(post, mediaByPost.getOrDefault(post.getId(), List.of())))
                .toList();
        return postFeatureMapper.toPagedResponse(posts, content);
    }

    private List<UUID> joinedGroupIds() {
        try {
            ApiResponse<List<UUID>> response = userConnectionClient.getJoinedGroupIds();
            return response == null || response.getData() == null ? List.of() : response.getData();
        } catch (Exception e) {
            log.error("Could not load the groups of the viewer: {}", e.getMessage());
            throw new BusinessException(PostServiceErrorCode.GROUP_SERVICE_UNAVAILABLE);
        }
    }
}
