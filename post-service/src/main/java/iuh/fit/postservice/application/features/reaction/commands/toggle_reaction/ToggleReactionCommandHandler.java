package iuh.fit.postservice.application.features.reaction.commands.toggle_reaction;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.application.mapper.ReactionFeatureMapper;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.entities.Reaction;
import iuh.fit.postservice.domain.enums.ReactionType;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.ReactionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
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

    @Transactional
    public ToggleReactionResult handle(ToggleReactionCommand command) {
        Post post = postRepository.findByIdAndDeletedFalse(command.getPostId())
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));

        Optional<Reaction> existingReaction = reactionRepository.findByPostIdAndUserId(command.getPostId(), command.getUserId());
        ReactionType activeType = null;

        if (existingReaction.isPresent()) {
            Reaction reaction = existingReaction.get();
            if (reaction.getType() == command.getType()) {
                // Remove reaction (toggle off)
                reactionRepository.deleteByPostIdAndUserId(command.getPostId(), command.getUserId());
                post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
            } else {
                // Change reaction type
                reaction.setType(command.getType());
                reactionRepository.save(reaction);
                activeType = command.getType();
            }
        } else {
            // Add new reaction
            Reaction newReaction = reactionFeatureMapper.toEntity(command);
            reactionRepository.save(newReaction);
            post.setLikeCount(post.getLikeCount() + 1);
            activeType = command.getType();
        }

        postRepository.save(post);
        long totalReactions = reactionRepository.countByPostId(command.getPostId());

        // Publish notification event to Kafka if not self-reacting (UC-NO01)
        if (activeType != null && post.getAuthorId() != null && !post.getAuthorId().equals(command.getUserId())) {
            try {
                Map<String, Object> notifEvent = new HashMap<>();
                notifEvent.put("recipientId", post.getAuthorId().toString());
                notifEvent.put("actorId", command.getUserId().toString());
                notifEvent.put("type", "LIKE_POST");
                notifEvent.put("title", "Tương tác bài viết");
                notifEvent.put("content", "Một người dùng đã bày tỏ cảm xúc về bài viết của bạn.");
                notifEvent.put("targetId", post.getId().toString());
                notifEvent.put("targetUrl", "/posts/" + post.getId());
                notifEvent.put("avatarUrl", null);

                kafkaTemplate.send("notification.in-app.send", notifEvent);
                log.info("Published LIKE_POST notification event to Kafka for author: {}", post.getAuthorId());
            } catch (Exception e) {
                log.warn("Failed to publish LIKE_POST notification event: {}", e.getMessage());
            }
        }

        return reactionFeatureMapper.toToggleResult(command.getPostId(), command.getUserId(), activeType, totalReactions);
    }
}

