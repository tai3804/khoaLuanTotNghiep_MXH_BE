package iuh.fit.postservice.infrastructure.event;

import iuh.fit.commonframework.event.PostModeratedEvent;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostModeratedEventListener {

    PostRepository postRepository;

    @KafkaListener(topics = "post.moderated", groupId = "post-moderation-group")
    public void handlePostModerated(PostModeratedEvent event) {
        log.info("Received PostModeratedEvent for postId: {}, action: {}, reason: {}",
                event.getPostId(), event.getAction(), event.getReason());

        Optional<Post> postOpt = postRepository.findById(event.getPostId());
        if (postOpt.isPresent()) {
            Post post = postOpt.get();
            post.setDeleted(true);
            postRepository.save(post);
            log.info("Successfully soft-deleted moderated post: {}", event.getPostId());
        } else {
            log.warn("Post with ID {} not found for moderation event", event.getPostId());
        }
    }
}
