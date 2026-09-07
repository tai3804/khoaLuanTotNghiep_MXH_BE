package iuh.fit.mediaservice.infrastructure.event;

import iuh.fit.commonframework.event.PostCreatedEvent;
import iuh.fit.commonframework.event.PostMediaUploadedEvent;
import iuh.fit.mediaservice.domain.enums.MediaType;
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
    KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "post.created", groupId = "media-group")
    public void handlePostCreated(PostCreatedEvent event) {
        log.info("Received PostCreatedEvent for postId: {}", event.getPostId());

        if (event.getFiles() == null || event.getFiles().isEmpty()) {
            return;
        }

        List<PostMediaUploadedEvent.MediaItemPayload> mediaItemList = new ArrayList<>();
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
                MediaType mediaType = awsS3StorageService.determineMediaType(filePayload.getContentType());

                PostMediaUploadedEvent.MediaItemPayload item = PostMediaUploadedEvent.MediaItemPayload.builder()
                        .fileUrl(fileUrl)
                        .fileKey(fileKey)
                        .mediaType(mediaType.name())
                        .fileSize(filePayload.getData() != null ? filePayload.getData().length : 0)
                        .sortOrder(filePayload.getSortOrder())
                        .build();

                mediaItemList.add(item);
            }
        } catch (Exception e) {
            log.error("Failed to process media upload for postId {}: {}", event.getPostId(), e.getMessage(), e);
            success = false;
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
