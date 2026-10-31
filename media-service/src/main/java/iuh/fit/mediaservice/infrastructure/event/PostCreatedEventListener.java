package iuh.fit.mediaservice.infrastructure.event;

import iuh.fit.commonframework.event.PostCreatedEvent;
import iuh.fit.commonframework.event.PostMediaUploadedEvent;
import iuh.fit.mediaservice.domain.entities.Media;
import iuh.fit.mediaservice.domain.enums.MediaType;
import iuh.fit.mediaservice.domain.repository.MediaRepository;
import iuh.fit.mediaservice.infrastructure.storage.AwsS3StorageService;
import iuh.fit.mediaservice.infrastructure.util.ByteArrayMultipartFile;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostCreatedEventListener {

    AwsS3StorageService awsS3StorageService;
    MediaRepository mediaRepository;
    KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "post.created", groupId = "media-group")
    public void handlePostCreated(PostCreatedEvent event) {
        if (event == null) {
            return;
        }
        log.info("Received PostCreatedEvent for postId: {}", event.getPostId());

        if (event.getFiles() == null || event.getFiles().isEmpty()) {
            return;
        }

        List<PostMediaUploadedEvent.MediaItemPayload> mediaItemList = new ArrayList<>();
        List<String> uploadedFileKeys = new ArrayList<>();
        boolean success = true;

        try {
            for (PostCreatedEvent.MediaPayload filePayload : event.getFiles()) {
                MultipartFile multipartFile = new ByteArrayMultipartFile(
                        filePayload.getData(),
                        filePayload.getFileName(),
                        filePayload.getContentType()
                );

                String fileUrl = awsS3StorageService.uploadFile(multipartFile, "posts");
                String fileKey = awsS3StorageService.extractFileKeyFromUrl(fileUrl);
                uploadedFileKeys.add(fileKey);

                MediaType mediaType = awsS3StorageService.determineMediaType(filePayload.getContentType());
                long fileSize = filePayload.getData() != null ? filePayload.getData().length : 0L;

                if (event.getAuthorId() != null) {
                    Media mediaEntity = Media.builder()
                            .userId(event.getAuthorId())
                            .fileKey(fileKey)
                            .fileUrl(fileUrl)
                            .originalFilename(filePayload.getFileName())
                            .fileSize(fileSize)
                            .mediaType(mediaType)
                            .folder("posts")
                            .build();
                    mediaRepository.save(mediaEntity);
                }

                PostMediaUploadedEvent.MediaItemPayload item = PostMediaUploadedEvent.MediaItemPayload.builder()
                        .fileUrl(fileUrl)
                        .fileKey(fileKey)
                        .mediaType(mediaType.name())
                        .fileSize(fileSize)
                        .sortOrder(filePayload.getSortOrder())
                        .build();

                mediaItemList.add(item);
            }
        } catch (Exception e) {
            log.error("Failed to process media upload for postId {}: {}", event.getPostId(), e.getMessage(), e);
            success = false;
            mediaItemList.clear();

            // Roll back any S3 files and DB records created during this failed batch
            for (String uploadedKey : uploadedFileKeys) {
                try {
                    awsS3StorageService.deleteFile(uploadedKey);
                } catch (Exception cleanupEx) {
                    log.warn("Failed to rollback S3 file [{}] after upload failure: {}", uploadedKey, cleanupEx.getMessage());
                }
                try {
                    mediaRepository.deleteByFileKey(uploadedKey);
                } catch (Exception cleanupEx) {
                    log.warn("Failed to rollback Media DB record [{}] after upload failure: {}", uploadedKey, cleanupEx.getMessage());
                }
            }
        }

        PostMediaUploadedEvent uploadedEvent = PostMediaUploadedEvent.builder()
                .postId(event.getPostId())
                .success(success)
                .mediaList(mediaItemList)
                .build();

        kafkaTemplate.send("post.media.uploaded", uploadedEvent);
        log.info("Published PostMediaUploadedEvent for postId: {}, success: {}", event.getPostId(), success);
    }
}
