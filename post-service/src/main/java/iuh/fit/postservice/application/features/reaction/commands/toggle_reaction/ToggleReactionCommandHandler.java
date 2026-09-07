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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ToggleReactionCommandHandler {

    PostRepository postRepository;
    ReactionRepository reactionRepository;
    ReactionFeatureMapper reactionFeatureMapper;

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

        return reactionFeatureMapper.toToggleResult(command.getPostId(), command.getUserId(), activeType, totalReactions);
    }
}
