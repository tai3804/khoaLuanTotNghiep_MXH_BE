package iuh.fit.mediaservice.infrastructure.event;

import iuh.fit.commonframework.event.UserAvatarUpdatedEvent;
import iuh.fit.mediaservice.domain.repository.MediaRepository;
import iuh.fit.mediaservice.infrastructure.storage.AwsS3StorageService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserAvatarUpdatedEventListener {

    AwsS3StorageService awsS3StorageService;
    MediaRepository mediaRepository;

    @Transactional
    @KafkaListener(topics = "user.avatar.updated", groupId = "media-group")
    public void handleUserAvatarUpdated(UserAvatarUpdatedEvent event) {
        log.info("Received UserAvatarUpdatedEvent for userId: {}", event.getUserId());

        deleteOldMediaFile(event.getOldAvatarUrl(), event.getNewAvatarUrl());
        deleteOldMediaFile(event.getOldCoverUrl(), event.getNewCoverUrl());
    }

    private void deleteOldMediaFile(String oldUrl, String newUrl) {
        if (oldUrl == null || oldUrl.isBlank() || oldUrl.equals(newUrl)) {
            return;
        }

        try {
            String oldFileKey = awsS3StorageService.extractFileKeyFromUrl(oldUrl);
            if (oldFileKey != null && !oldFileKey.isBlank()) {
                awsS3StorageService.deleteFile(oldFileKey);
                mediaRepository.deleteByFileKey(oldFileKey);
                log.info("Successfully deleted old avatar/cover file from S3 and DB: {}", oldFileKey);
            }
        } catch (Exception e) {
            log.error("Failed to delete old avatar/cover file [{}]: {}", oldUrl, e.getMessage());
        }
    }
}
