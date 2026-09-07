package iuh.fit.postservice.application.features.post.commands.create_post;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.event.PostCreatedEvent;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.application.mapper.PostFeatureMapper;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.enums.PostPrivacy;
import iuh.fit.postservice.domain.enums.PostStatus;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CreatePostCommandHandler {

    PostRepository postRepository;
    PostFeatureMapper postFeatureMapper;
    KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public CreatePostResult handle(CreatePostCommand command) {
        boolean hasContent = command.getContent() != null && !command.getContent().isBlank();
        boolean hasFiles = command.getFiles() != null && !command.getFiles().isEmpty();

        if (!hasContent && !hasFiles) {
            throw new BusinessException(PostServiceErrorCode.INVALID_POST_CONTENT);
        }

        Post post = postFeatureMapper.toEntity(command);
        if (post.getPrivacy() == null) {
            post.setPrivacy(PostPrivacy.PUBLIC);
        }

        // Only list allowedUserIds when privacy is CUSTOM
        if (post.getPrivacy() != PostPrivacy.CUSTOM) {
            post.setAllowedUserIds(new HashSet<>());
        } else if (post.getAllowedUserIds() == null) {
            post.setAllowedUserIds(new HashSet<>());
        }

        if (hasFiles) {
            post.setStatus(PostStatus.PROCESSING);
        } else {
            post.setStatus(PostStatus.PUBLISHED);
        }

        Post savedPost = postRepository.save(post);

        if (hasFiles) {
            List<PostCreatedEvent.MediaPayload> mediaPayloads = new ArrayList<>();
            int sortOrder = 0;

            for (MultipartFile file : command.getFiles()) {
                if (file != null && !file.isEmpty()) {
                    try {
                        PostCreatedEvent.MediaPayload payload = PostCreatedEvent.MediaPayload.builder()
                                .fileName(file.getOriginalFilename())
                                .contentType(file.getContentType())
                                .data(file.getBytes())
                                .sortOrder(sortOrder++)
                                .build();
                        mediaPayloads.add(payload);
                    } catch (Exception e) {
                        log.error("Failed to read bytes for file {}: {}", file.getOriginalFilename(), e.getMessage());
                    }
                }
            }

            if (!mediaPayloads.isEmpty()) {
                PostCreatedEvent event = PostCreatedEvent.builder()
                        .postId(savedPost.getId())
                        .authorId(savedPost.getAuthorId())
                        .files(mediaPayloads)
                        .build();

                kafkaTemplate.send("post.created", event);
                log.info("Published PostCreatedEvent to Kafka topic 'post.created' for postId: {}", savedPost.getId());
            }
        }

        return postFeatureMapper.toCreateResult(savedPost, List.of());
    }
}
