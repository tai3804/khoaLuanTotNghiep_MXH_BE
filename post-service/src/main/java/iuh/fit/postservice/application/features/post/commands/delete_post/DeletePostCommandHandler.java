package iuh.fit.postservice.application.features.post.commands.delete_post;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.event.PostDeletedEvent;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.application.service.ranking.RankedFeedCache;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.entities.PostMedia;
import iuh.fit.postservice.infrastructure.cache.PostCacheEvictor;
import iuh.fit.postservice.infrastructure.persistence.repository.PostMediaRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeletePostCommandHandler {

    PostRepository postRepository;
    PostMediaRepository postMediaRepository;
    JwtUtil jwtUtil;
    RankedFeedCache rankedFeedCache;
    PostCacheEvictor postCacheEvictor;
    KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    @CacheEvict(cacheNames = {"post-feed-v2", "post-user-feed-v2", "post-detail-v2"}, allEntries = true)
    public void handle(DeletePostCommand command) {
        Post post = postRepository.findByIdAndDeletedFalse(command.getPostId())
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));

        List<String> roles = jwtUtil.getCurrentUserRoles();
        boolean isAdminOrMod = roles != null && (
                roles.contains("ROLE_ADMIN") ||
                roles.contains("ADMIN") ||
                roles.contains("ROLE_MODERATOR") ||
                roles.contains("MODERATOR")
        );

        if (!post.getAuthorId().equals(command.getUserId()) && !isAdminOrMod) {
            throw new BusinessException(PostServiceErrorCode.UNAUTHORIZED_ACTION);
        }

        List<PostMedia> mediaList = postMediaRepository.findByPostIdOrderBySortOrderAsc(post.getId());
        List<String> fileKeys = new ArrayList<>();
        for (PostMedia media : mediaList) {
            String targetKey = (media.getFileKey() != null && !media.getFileKey().isBlank())
                    ? media.getFileKey()
                    : media.getFileUrl();
            if (targetKey != null && !targetKey.isBlank()) {
                fileKeys.add(targetKey);
            }
        }

        post.setDeleted(true);
        post.setGroupPinnedAt(null);
        postRepository.save(post);
        postMediaRepository.deleteByPostId(post.getId());

        rankedFeedCache.evictViewer(post.getAuthorId());
        postCacheEvictor.evictAllWithRecheck();

        try {
            kafkaTemplate.send("post.deleted", PostDeletedEvent.builder()
                    .postId(post.getId())
                    .authorId(post.getAuthorId())
                    .fileKeys(fileKeys)
                    .build());
            log.info("Published PostDeletedEvent for post {} with {} media key(s)", post.getId(), fileKeys.size());
        } catch (Exception e) {
            log.error("Failed to publish PostDeletedEvent for post {}: {}", post.getId(), e.getMessage());
        }
    }
}
