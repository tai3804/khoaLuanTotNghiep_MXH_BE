package iuh.fit.postservice.application.features.post.queries.get_all_posts;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import iuh.fit.commonframework.infrastructure.filter.SortDirection;
import iuh.fit.postservice.application.features.post.queries.get_post_detail.GetPostDetailResult;
import iuh.fit.postservice.application.mapper.PostFeatureMapper;
import iuh.fit.postservice.application.service.PostSeenService;
import iuh.fit.postservice.application.service.PostVisibilityService;
import iuh.fit.postservice.application.service.ranking.FeedRankingService;
import iuh.fit.postservice.application.service.ranking.PostRelevanceScorer;
import iuh.fit.postservice.application.service.ranking.RankedFeedSnapshot;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.entities.PostMedia;
import iuh.fit.postservice.domain.entities.PostView;
import iuh.fit.postservice.domain.enums.MediaType;
import iuh.fit.postservice.domain.enums.PostStatus;
import iuh.fit.postservice.infrastructure.persistence.repository.PostMediaRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetAllPostsQueryHandler {

    PostRepository postRepository;
    PostMediaRepository postMediaRepository;
    PostFeatureMapper postFeatureMapper;
    PostVisibilityService postVisibilityService;
    FeedRankingService feedRankingService;
    PostSeenService postSeenService;

    @Transactional(readOnly = true)
    // Kết quả "chỉ bài chưa xem" phụ thuộc lượt xem vừa ghi nên không cache 30 giây như các truy vấn khác
    @Cacheable(cacheNames = "post-feed-v2", condition = "!#query.unseenOnly", key = "#query.viewerId + ':' + #query.cursor + ':' + #query.filter.page + ':' + #query.filter.size + ':' + #query.filter.keyword + ':' + #query.filter.sortBy + ':' + #query.filter.sortDirection + ':' + #query.filter.filters")
    public PagedResponse<GetPostDetailResult> handle(GetAllPostsQuery query) {
        BaseFilter filter = query.getFilter() != null ? query.getFilter() : new BaseFilter();

        // Bảng tin cá nhân hoá: sortBy=relevance (Tất cả) hoặc sortBy=trending (Nổi bật)
        PostRelevanceScorer.Mode rankMode = rankModeOf(query, filter);
        if (rankMode != null) {
            return handleRanked(query, filter, rankMode);
        }

        Specification<Post> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("deleted"), false));
            // Pending/rejected group posts are never exposed through public/group feeds.
            predicates.add(cb.equal(root.get("status"), PostStatus.PUBLISHED));

            if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
                String searchPattern = "%" + filter.getKeyword().toLowerCase().trim() + "%";
                predicates.add(cb.like(cb.lower(root.get("content")), searchPattern));
            }

            // Cursor-based filter: if cursor is provided (ISO LocalDateTime string or epoch millis)
            if (query.getCursor() != null && !query.getCursor().isBlank()) {
                try {
                    java.time.LocalDateTime cursorDateTime = java.time.LocalDateTime.parse(query.getCursor().trim());
                    predicates.add(cb.lessThan(root.get("createdAt"), cursorDateTime));
                } catch (Exception e) {
                    try {
                        long epochMillis = Long.parseLong(query.getCursor().trim());
                        java.time.LocalDateTime cursorDateTime = java.time.Instant.ofEpochMilli(epochMillis)
                                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
                        predicates.add(cb.lessThan(root.get("createdAt"), cursorDateTime));
                    } catch (Exception ignored) {}
                }
            }

            if (filter.getFilters() != null) {
                for (Map.Entry<String, Object> entry : filter.getFilters().entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        if ("hasVideo".equals(entry.getKey()) && "true".equalsIgnoreCase(entry.getValue().toString())) {
                            jakarta.persistence.criteria.Subquery<java.util.UUID> subquery = q.subquery(java.util.UUID.class);
                            jakarta.persistence.criteria.Root<PostMedia> mediaRoot = subquery.from(PostMedia.class);
                            subquery.select(mediaRoot.get("postId"))
                                    .where(
                                        cb.and(
                                            cb.equal(mediaRoot.get("postId"), root.get("id")),
                                            cb.equal(mediaRoot.get("mediaType"), MediaType.VIDEO)
                                        )
                                    );
                            predicates.add(cb.exists(subquery));
                            continue;
                        }
                        if ("hashtag".equalsIgnoreCase(entry.getKey())) {
                            String cleanTag = entry.getValue().toString().trim().toLowerCase();
                            if (cleanTag.startsWith("#")) cleanTag = cleanTag.substring(1);
                            predicates.add(cb.isMember(cleanTag, root.get("hashtags")));
                            continue;
                        }
                        try {
                            predicates.add(cb.equal(root.get(entry.getKey()), entry.getValue()));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
            if (filter.getFilters() == null || !filter.getFilters().containsKey("isArchived")) {
                predicates.add(cb.equal(root.get("isArchived"), false));
            }
            // Bỏ bài đã xem (còn hiệu lực), nhưng luôn giữ bài của chính người xem
            if (query.isUnseenOnly() && query.getViewerId() != null) {
                jakarta.persistence.criteria.Subquery<UUID> seenSub = q.subquery(UUID.class);
                jakarta.persistence.criteria.Root<PostView> viewRoot = seenSub.from(PostView.class);
                seenSub.select(viewRoot.get("postId")).where(
                        cb.equal(viewRoot.get("postId"), root.get("id")),
                        cb.equal(viewRoot.get("userId"), query.getViewerId()),
                        cb.greaterThanOrEqualTo(viewRoot.get("seenAt"), postSeenService.retentionCutoff()));
                predicates.add(cb.or(cb.equal(root.get("authorId"), query.getViewerId()), cb.not(cb.exists(seenSub))));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        boolean hasCursor = query.getCursor() != null && !query.getCursor().isBlank();
        int pageIndex = hasCursor ? 0 : Math.max(0, filter.getPage() - 1);
        int pageSize = filter.getSize() > 0 ? Math.min(filter.getSize(), 100) : 10;
        String sortBy = (filter.getSortBy() != null && !filter.getSortBy().isBlank()) ? filter.getSortBy() : "createdAt";
        // "relevance"/"trending" là chế độ xếp hạng, không phải cột: khách chưa đăng nhập, tìm kiếm hoặc lọc thì dùng thứ tự thời gian
        if ("relevance".equalsIgnoreCase(sortBy) || "trending".equalsIgnoreCase(sortBy)) sortBy = "createdAt";
        Sort.Direction direction = filter.getSortDirection() == SortDirection.DESC ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Order.desc("isPinned"), new Sort.Order(direction, sortBy)));

        Page<Post> postsPage = postRepository.findAll(spec, pageable);

        boolean mainFeed = filter.getFilters() == null || !Boolean.TRUE.equals(filter.getFilters().get("_groupFeed"));
        Set<java.util.UUID> visiblePostIds = postVisibilityService.visiblePostIds(postsPage.getContent(), query.getViewerId(), mainFeed);
        List<Post> visiblePosts = postsPage.getContent().stream().filter(post -> visiblePostIds.contains(post.getId())).toList();
        // Một truy vấn lấy ảnh/video cho cả trang thay vì một truy vấn cho từng bài
        Map<UUID, List<PostMedia>> mediaByPost = loadMediaByPost(visiblePosts.stream().map(Post::getId).toList());
        List<GetPostDetailResult> content = visiblePosts.stream()
                .map(post -> postFeatureMapper.toGetDetailResult(post, mediaByPost.getOrDefault(post.getId(), List.of())))
                .toList();

        return postFeatureMapper.toPagedResponse(postsPage, content);
    }

    private static PostRelevanceScorer.Mode rankModeOf(GetAllPostsQuery query, BaseFilter filter) {
        if (query.getViewerId() == null) return null; // khách chưa đăng nhập: không có gì để cá nhân hoá
        if (query.getCursor() != null && !query.getCursor().isBlank()) return null;
        if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) return null;
        if (filter.getFilters() != null && !filter.getFilters().isEmpty()) return null;
        if ("relevance".equalsIgnoreCase(filter.getSortBy())) return PostRelevanceScorer.Mode.RELEVANCE;
        if ("trending".equalsIgnoreCase(filter.getSortBy())) return PostRelevanceScorer.Mode.TRENDING;
        return null;
    }

    /**
     * Trang của bảng tin đã xếp hạng: cắt từ bản chụp đã xếp hạng (cache) rồi nạp bài viết theo đúng thứ tự đó.
     * Phân trang theo số trang (page bắt đầu từ 1), không dùng cursor theo thời gian.
     */
    private PagedResponse<GetPostDetailResult> handleRanked(GetAllPostsQuery query, BaseFilter filter, PostRelevanceScorer.Mode mode) {
        int page = Math.max(1, filter.getPage());
        // Trang 1 của bảng tin "chưa xem" luôn tính lại để bài vừa xem biến mất sau khi tải lại trang
        RankedFeedSnapshot snapshot = feedRankingService.snapshot(query.getViewerId(), mode, query.isUnseenOnly(),
                query.isUnseenOnly() && page == 1);

        int size = filter.getSize() > 0 ? Math.min(filter.getSize(), 50) : 10;
        int total = snapshot.getPostIds().size();
        int from = Math.min((page - 1) * size, total);
        int to = Math.min(from + size, total);

        List<UUID> pageIds = snapshot.getPostIds().subList(from, to).stream().map(UUID::fromString).toList();
        Map<UUID, Post> postsById = postRepository.findAllById(pageIds).stream()
                .collect(Collectors.toMap(Post::getId, p -> p));
        Map<UUID, List<PostMedia>> mediaByPost = loadMediaByPost(pageIds);

        List<GetPostDetailResult> content = new ArrayList<>(pageIds.size());
        for (int i = 0; i < pageIds.size(); i++) {
            Post post = postsById.get(pageIds.get(i));
            // Bản chụp có thể cũ vài chục giây: bỏ bài vừa bị xoá/ẩn
            if (post == null || post.isDeleted() || post.isArchived()) continue;
            GetPostDetailResult result = postFeatureMapper.toGetDetailResult(post, mediaByPost.getOrDefault(post.getId(), List.of()));
            result.setRelevanceScore(snapshot.getScores().get(from + i));
            result.setRelevanceReason(snapshot.getReasons().get(from + i));
            content.add(result);
        }

        int totalPages = (int) Math.ceil((double) total / size);
        return PagedResponse.<GetPostDetailResult>builder()
                .content(content)
                .page(page - 1)
                .size(size)
                .totalElements(total)
                .totalPages(totalPages)
                .last(to >= total)
                .build();
    }

    private Map<UUID, List<PostMedia>> loadMediaByPost(List<UUID> postIds) {
        if (postIds.isEmpty()) return new HashMap<>();
        return postMediaRepository.findByPostIdInOrderBySortOrderAsc(postIds).stream()
                .collect(Collectors.groupingBy(PostMedia::getPostId));
    }
}
