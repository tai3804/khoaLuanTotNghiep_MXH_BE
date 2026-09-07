package iuh.fit.feedservice.infrastructure.event;

import iuh.fit.commonframework.event.PostCreatedEvent;
import iuh.fit.commonframework.event.PostModeratedEvent;
import iuh.fit.feedservice.application.features.feed.commands.process_post_created.ProcessPostCreatedCommand;
import iuh.fit.feedservice.application.features.feed.commands.process_post_created.ProcessPostCreatedCommandHandler;
import iuh.fit.feedservice.application.features.feed.commands.process_post_deleted.ProcessPostDeletedCommand;
import iuh.fit.feedservice.application.features.feed.commands.process_post_deleted.ProcessPostDeletedCommandHandler;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostEventListener {

    ProcessPostCreatedCommandHandler processPostCreatedCommandHandler;
    ProcessPostDeletedCommandHandler processPostDeletedCommandHandler;

    @KafkaListener(topics = "post.created", groupId = "feed-service-group")
    public void handlePostCreated(PostCreatedEvent event) {
        log.info("Received PostCreatedEvent in feed-service for postId: {}", event.getPostId());
        try {
            ProcessPostCreatedCommand command = ProcessPostCreatedCommand.builder()
                    .postId(event.getPostId())
                    .authorId(event.getAuthorId())
                    .createdAt(LocalDateTime.now())
                    .build();
            processPostCreatedCommandHandler.handle(command);
        } catch (Exception e) {
            log.error("Failed to process PostCreatedEvent in feed-service", e);
        }
    }

    @KafkaListener(topics = "post.moderated", groupId = "feed-service-group")
    public void handlePostModerated(PostModeratedEvent event) {
        log.info("Received PostModeratedEvent in feed-service for postId: {}", event.getPostId());
        try {
            ProcessPostDeletedCommand command = ProcessPostDeletedCommand.builder()
                    .postId(event.getPostId())
                    .build();
            processPostDeletedCommandHandler.handle(command);
        } catch (Exception e) {
            log.error("Failed to process PostModeratedEvent in feed-service", e);
        }
    }
}
