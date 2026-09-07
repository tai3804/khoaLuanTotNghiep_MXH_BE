package iuh.fit.postservice.application.features.post.queries.get_post_detail;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.application.mapper.PostFeatureMapper;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.entities.PostMedia;
import iuh.fit.postservice.infrastructure.persistence.repository.PostMediaRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetPostDetailQueryHandler {

    PostRepository postRepository;
    PostMediaRepository postMediaRepository;
    PostFeatureMapper postFeatureMapper;

    @Transactional(readOnly = true)
    public GetPostDetailResult handle(GetPostDetailQuery query) {
        Post post = postRepository.findByIdAndDeletedFalse(query.getPostId())
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));

        List<PostMedia> mediaList = postMediaRepository.findByPostIdOrderBySortOrderAsc(post.getId());

        return postFeatureMapper.toGetDetailResult(post, mediaList);
    }
}
