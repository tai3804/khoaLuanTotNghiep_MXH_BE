package iuh.fit.postservice.application.features.post.queries.get_all_posts;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import iuh.fit.commonframework.infrastructure.filter.SortDirection;
import iuh.fit.postservice.application.features.post.queries.get_post_detail.GetPostDetailResult;
import iuh.fit.postservice.application.mapper.PostFeatureMapper;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.entities.PostMedia;
import iuh.fit.postservice.domain.enums.MediaType;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetAllPostsQueryHandler {

    PostRepository postRepository;
    PostMediaRepository postMediaRepository;
    PostFeatureMapper postFeatureMapper;

    @Transactional(readOnly = true)
    public PagedResponse<GetPostDetailResult> handle(GetAllPostsQuery query) {
        BaseFilter filter = query.getFilter() != null ? query.getFilter() : new BaseFilter();

        Specification<Post> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("deleted"), false));

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
                        try {
                            predicates.add(cb.equal(root.get(entry.getKey()), entry.getValue()));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        boolean hasCursor = query.getCursor() != null && !query.getCursor().isBlank();
        int pageIndex = hasCursor ? 0 : Math.max(0, filter.getPage() - 1);
        int pageSize = filter.getSize() > 0 ? Math.min(filter.getSize(), 100) : 10;
        String sortBy = (filter.getSortBy() != null && !filter.getSortBy().isBlank()) ? filter.getSortBy() : "createdAt";
        Sort.Direction direction = filter.getSortDirection() == SortDirection.DESC ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(direction, sortBy));

        Page<Post> postsPage = postRepository.findAll(spec, pageable);

        List<GetPostDetailResult> content = postsPage.getContent().stream().map(post -> {
            List<PostMedia> mediaList = postMediaRepository.findByPostIdOrderBySortOrderAsc(post.getId());
            return postFeatureMapper.toGetDetailResult(post, mediaList);
        }).toList();

        return postFeatureMapper.toPagedResponse(postsPage, content);
    }
}
