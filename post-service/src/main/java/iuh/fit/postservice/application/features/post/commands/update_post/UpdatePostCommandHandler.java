package iuh.fit.postservice.application.features.post.commands.update_post;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.application.mapper.PostFeatureMapper;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.entities.PostMedia;
import iuh.fit.postservice.domain.enums.PostPrivacy;
import iuh.fit.postservice.infrastructure.persistence.repository.PostMediaRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UpdatePostCommandHandler {

    PostRepository postRepository;
    PostMediaRepository postMediaRepository;
    PostFeatureMapper postFeatureMapper;

    @Transactional
    public UpdatePostResult handle(UpdatePostCommand command) {
        Post post = postRepository.findByIdAndDeletedFalse(command.getPostId())
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));

        if (!post.getAuthorId().equals(command.getUserId())) {
            throw new BusinessException(PostServiceErrorCode.UNAUTHORIZED_ACTION);
        }

        if (command.getContent() != null) {
            post.setContent(command.getContent());
        }
        if (command.getPrivacy() != null) {
            post.setPrivacy(command.getPrivacy());
            if (command.getPrivacy() != PostPrivacy.CUSTOM) {
                post.setAllowedUserIds(new HashSet<>());
            } else if (command.getAllowedUserIds() != null) {
                post.setAllowedUserIds(command.getAllowedUserIds());
            }
        } else if (post.getPrivacy() == PostPrivacy.CUSTOM && command.getAllowedUserIds() != null) {
            post.setAllowedUserIds(command.getAllowedUserIds());
        }

        Post updatedPost = postRepository.save(post);
        List<PostMedia> mediaList = postMediaRepository.findByPostIdOrderBySortOrderAsc(updatedPost.getId());

        return postFeatureMapper.toUpdateResult(updatedPost, mediaList);
    }
}
