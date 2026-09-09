package iuh.fit.postservice.application.features.saved_post.commands.save_post;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.application.exception.ErrorCode;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.entities.SavedPost;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.SavedPostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SavePostHandler {

    PostRepository postRepository;
    SavedPostRepository savedPostRepository;

    @Transactional
    public SavedPost handle(SavePostCommand command) {
        Post post = postRepository.findByIdAndDeletedFalse(command.getPostId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        Optional<SavedPost> existingOpt = savedPostRepository.findByUserIdAndPostId(command.getUserId(), post.getId());
        if (existingOpt.isPresent()) {
            SavedPost existing = existingOpt.get();
            if (command.getCollectionName() != null) {
                existing.setCollectionName(command.getCollectionName());
            }
            log.info("Updated collection name for saved post {} for user {}", post.getId(), command.getUserId());
            return savedPostRepository.save(existing);
        }

        SavedPost savedPost = SavedPost.builder()
                .userId(command.getUserId())
                .postId(post.getId())
                .collectionName(command.getCollectionName() != null ? command.getCollectionName() : "DEFAULT")
                .build();

        SavedPost saved = savedPostRepository.save(savedPost);
        log.info("Saved post {} to collection {} for user {}", post.getId(), saved.getCollectionName(), command.getUserId());
        return saved;
    }
}
