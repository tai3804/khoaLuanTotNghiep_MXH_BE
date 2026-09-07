package iuh.fit.postservice.application.features.post.commands.share_post;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.application.mapper.PostFeatureMapper;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.enums.PostPrivacy;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SharePostCommandHandler {

    PostRepository postRepository;
    PostFeatureMapper postFeatureMapper;

    @Transactional
    public SharePostResult handle(SharePostCommand command) {
        Post originalPost = postRepository.findByIdAndDeletedFalse(command.getOriginalPostId())
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));

        Post sharedPost = postFeatureMapper.toSharedPostEntity(command);
        if (sharedPost.getPrivacy() == null) {
            sharedPost.setPrivacy(PostPrivacy.PUBLIC);
        }

        if (sharedPost.getPrivacy() != PostPrivacy.CUSTOM) {
            sharedPost.setAllowedUserIds(new HashSet<>());
        } else if (sharedPost.getAllowedUserIds() == null) {
            sharedPost.setAllowedUserIds(new HashSet<>());
        }

        Post savedPost = postRepository.save(sharedPost);

        // Increment share count on original post
        originalPost.setShareCount(originalPost.getShareCount() + 1);
        postRepository.save(originalPost);

        return postFeatureMapper.toShareResult(savedPost);
    }
}
