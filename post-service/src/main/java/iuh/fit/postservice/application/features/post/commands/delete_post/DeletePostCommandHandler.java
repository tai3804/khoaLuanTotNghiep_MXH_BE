package iuh.fit.postservice.application.features.post.commands.delete_post;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.entities.PostMedia;
import iuh.fit.postservice.infrastructure.persistence.repository.PostMediaRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import iuh.fit.postservice.infrastructure.client.media.MediaClient;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeletePostCommandHandler {

    PostRepository postRepository;
    PostMediaRepository postMediaRepository;
    MediaClient mediaClient;

    @Transactional
    public void handle(DeletePostCommand command) {
        Post post = postRepository.findByIdAndDeletedFalse(command.getPostId())
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));

        if (!post.getAuthorId().equals(command.getUserId())) {
            throw new BusinessException(PostServiceErrorCode.UNAUTHORIZED_ACTION);
        }

        post.setDeleted(true);
        postRepository.save(post);

        // Delete associated S3 files via MediaClient
        List<PostMedia> mediaList = postMediaRepository.findByPostIdOrderBySortOrderAsc(post.getId());
        for (PostMedia media : mediaList) {
            try {
                mediaClient.deleteFile(media.getFileKey());
            } catch (Exception e) {
                log.error("Failed to delete media file [{}] from S3: {}", media.getFileKey(), e.getMessage());
            }
        }
        postMediaRepository.deleteByPostId(post.getId());
    }
}
