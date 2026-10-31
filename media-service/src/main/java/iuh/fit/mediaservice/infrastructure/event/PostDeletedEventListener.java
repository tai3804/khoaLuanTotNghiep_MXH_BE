package iuh.fit.mediaservice.infrastructure.event;

import iuh.fit.commonframework.event.PostDeletedEvent;
import iuh.fit.mediaservice.domain.repository.MediaRepository;
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
    MediaRepository mediaRepository;

    @KafkaListener(topics = "post.deleted", groupId = "media-group")
    public void handlePostDeleted(PostDeletedEvent event) {
        if (event == null) {
            return;
        }
        log.info("Received PostDeletedEvent for postId: {}", event.getPostId());

        if (event.getFileKeys() == null || event.getFileKeys().isEmpty()) {
            return;
        }

        for (String fileKey : event.getFileKeys()) {
            if (fileKey == null || fileKey.isBlank()) {
                continue;
            }
            try {
                String actualKey = awsS3StorageService.extractFileKeyFromUrl(fileKey);
                awsS3StorageService.deleteFile(fileKey);
                mediaRepository.deleteByFileKey(actualKey);
                if (!actualKey.equals(fileKey)) {
                    mediaRepository.deleteByFileKey(fileKey);
                }
                log.info("Deleted S3 file and metadata [{}] for deleted postId: {}", actualKey, event.getPostId());
            } catch (Exception e) {
                log.error("Failed to delete S3 file [{}] for deleted postId {}: {}", fileKey, event.getPostId(), e.getMessage());
            }
        }
    }
}
