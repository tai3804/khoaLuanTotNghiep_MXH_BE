package iuh.fit.postservice.infrastructure.event;

import iuh.fit.commonframework.event.CommentModeratedEvent;
import iuh.fit.postservice.infrastructure.persistence.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommentModeratedEventListener {
    private final CommentRepository commentRepository;

    @KafkaListener(topics = "comment.moderated", groupId = "post-comment-moderation-group")
    public void handleCommentModerated(CommentModeratedEvent event) {
        commentRepository.findById(event.getCommentId()).ifPresentOrElse(comment -> {
            comment.setDeleted(true);
            commentRepository.save(comment);
            log.info("Deleted moderated comment: {}", event.getCommentId());
        }, () -> log.warn("Comment not found for moderation event: {}", event.getCommentId()));
    }
}
