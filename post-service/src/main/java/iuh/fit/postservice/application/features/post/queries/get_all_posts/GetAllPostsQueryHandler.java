package iuh.fit.postservice.application.features.post.queries.get_all_posts;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.commonframework.infrastructure.filter.BaseFilter;
import iuh.fit.commonframework.infrastructure.filter.SortDirection;
import iuh.fit.postservice.application.features.post.queries.get_post_detail.GetPostDetailResult;
import iuh.fit.postservice.application.mapper.PostFeatureMapper;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.entities.PostMedia;
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

            if (filter.getFilters() != null) {
                for (Map.Entry<String, Object> entry : filter.getFilters().entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        try {
                            predicates.add(cb.equal(root.get(entry.getKey()), entry.getValue()));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        int pageIndex = Math.max(0, filter.getPage() - 1);
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
