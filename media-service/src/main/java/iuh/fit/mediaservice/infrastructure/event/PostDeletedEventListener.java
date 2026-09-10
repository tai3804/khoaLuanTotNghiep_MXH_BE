package iuh.fit.mediaservice.infrastructure.event;

import iuh.fit.commonframework.event.PostDeletedEvent;
import iuh.fit.mediaservice.infrastructure.storage.AwsS3StorageService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostDeletedEventListener {

    AwsS3StorageService awsS3StorageService;

    @KafkaListener(topics = "post.deleted", groupId = "media-group")
    public void handlePostDeleted(PostDeletedEvent event) {
        log.info("Received PostDeletedEvent for postId: {}", event.getPostId());

        if (event.getFileKeys() == null || event.getFileKeys().isEmpty()) {
            return;
        }

        for (String fileKey : event.getFileKeys()) {
            try {
                awsS3StorageService.deleteFile(fileKey);
                log.info("Deleted S3 file [{}] for deleted postId: {}", fileKey, event.getPostId());
            } catch (Exception e) {
                log.error("Failed to delete S3 file [{}] for deleted postId {}: {}", fileKey, event.getPostId(), e.getMessage());
            }
        }
    }
}
