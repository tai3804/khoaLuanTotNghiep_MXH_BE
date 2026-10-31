package iuh.fit.postservice.application.features.reaction.commands.toggle_reaction;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.application.mapper.ReactionFeatureMapper;
import iuh.fit.postservice.application.service.PostVisibilityService;
import iuh.fit.postservice.application.service.ReputationGuard;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.entities.Reaction;
import iuh.fit.postservice.domain.enums.PostStatus;
import iuh.fit.postservice.domain.enums.ReactionType;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.ReactionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ToggleReactionCommandHandler {

    PostRepository postRepository;
    ReactionRepository reactionRepository;
    ReactionFeatureMapper reactionFeatureMapper;
    KafkaTemplate<String, Object> kafkaTemplate;
    ReputationGuard reputationGuard;
    PostVisibilityService postVisibilityService;

    @Transactional
    @CacheEvict(cacheNames = {"post-feed-v2", "post-user-feed-v2", "post-detail-v2"}, allEntries = true)
    public ToggleReactionResult handle(ToggleReactionCommand command) {
        reputationGuard.checkNotLocked(command.getUserId());
        Post post = postRepository.findByIdAndDeletedFalse(command.getPostId())
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));

        if (post.getStatus() != PostStatus.PUBLISHED
                || post.isArchived()
                || !postVisibilityService.canView(post, command.getUserId())) {
            throw new BusinessException(PostServiceErrorCode.POST_NOT_FOUND);
        }

        Optional<Reaction> existingReaction = reactionRepository.findByPostIdAndUserId(command.getPostId(), command.getUserId());
        ReactionType activeType = null;

        if (existingReaction.isPresent()) {
            Reaction reaction = existingReaction.get();
            if (reaction.getType() == command.getType()) {
                reactionRepository.deleteByPostIdAndUserId(command.getPostId(), command.getUserId());
            } else {
                reaction.setType(command.getType());
                reactionRepository.save(reaction);
                activeType = command.getType();
            }
        } else {
            Reaction newReaction = reactionFeatureMapper.toEntity(command);
            reactionRepository.saveAndFlush(newReaction);
            activeType = command.getType();
        }

        long totalReactions = reactionRepository.countByPostId(command.getPostId());
        post.setLikeCount(totalReactions);
        postRepository.save(post);

        if (activeType != null && post.getAuthorId() != null && !post.getAuthorId().equals(command.getUserId())) {
            try {
                Map<String, Object> notifEvent = new HashMap<>();
                notifEvent.put("recipientId", post.getAuthorId().toString());
                notifEvent.put("actorId", command.getUserId().toString());
                notifEvent.put("type", "LIKE_POST");
                notifEvent.put("title", "Tương tác bài viết");

                String reactionAction;
                switch (activeType) {
                    case LOVE -> reactionAction = "đã thả tim ❤️ về bài viết của bạn.";
                    case HAHA -> reactionAction = "đã bày tỏ haha 😆 về bài viết của bạn.";
                    case WOW -> reactionAction = "đã bày tỏ wow 😮 về bài viết của bạn.";
                    case SAD -> reactionAction = "đã bày tỏ buồn 😢 về bài viết của bạn.";
                    case ANGRY -> reactionAction = "đã bày tỏ phẫn nộ 😡 về bài viết của bạn.";
                    case LIKE -> reactionAction = "đã thích 👍 bài viết của bạn.";
                    default -> reactionAction = "đã bày tỏ cảm xúc về bài viết của bạn.";
                }

                notifEvent.put("content", "Một người dùng " + reactionAction);
                notifEvent.put("targetId", post.getId().toString());
                notifEvent.put("targetUrl", "/posts/" + post.getId() + "?reaction=" + activeType.name());
                notifEvent.put("avatarUrl", null);

                kafkaTemplate.send("notification.in-app.send", notifEvent);
                log.info("Published LIKE_POST notification event to Kafka for author: {}, reaction: {}", post.getAuthorId(), activeType);
            } catch (Exception e) {
                log.warn("Failed to publish LIKE_POST notification event: {}", e.getMessage());
            }
        }

        return reactionFeatureMapper.toToggleResult(command.getPostId(), command.getUserId(), activeType, totalReactions);
    }
}
