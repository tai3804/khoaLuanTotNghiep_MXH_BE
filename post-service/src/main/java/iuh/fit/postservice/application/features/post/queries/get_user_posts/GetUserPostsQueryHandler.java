package iuh.fit.postservice.application.features.post.queries.get_user_posts;

import iuh.fit.commonframework.application.dto.PagedResponse;
import iuh.fit.postservice.application.features.post.queries.get_post_detail.GetPostDetailResult;
import iuh.fit.postservice.application.mapper.PostFeatureMapper;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.entities.PostMedia;
import iuh.fit.postservice.infrastructure.persistence.repository.PostMediaRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetUserPostsQueryHandler {

    PostRepository postRepository;
    PostMediaRepository postMediaRepository;
    PostFeatureMapper postFeatureMapper;

    @Transactional(readOnly = true)
    public PagedResponse<GetPostDetailResult> handle(GetUserPostsQuery query) {
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), Sort.by("createdAt").descending());
        Page<Post> postsPage = postRepository.findByAuthorIdAndDeletedFalse(query.getUserId(), pageable);

        List<GetPostDetailResult> content = postsPage.getContent().stream().map(post -> {
            List<PostMedia> mediaList = postMediaRepository.findByPostIdOrderBySortOrderAsc(post.getId());
            return postFeatureMapper.toGetDetailResult(post, mediaList);
        }).toList();

        return postFeatureMapper.toPagedResponse(postsPage, content);
    }
}
