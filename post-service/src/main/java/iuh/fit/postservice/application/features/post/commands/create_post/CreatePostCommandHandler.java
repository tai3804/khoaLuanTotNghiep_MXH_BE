package iuh.fit.postservice.application.features.post.commands.create_post;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.event.PostCreatedEvent;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.application.mapper.PostFeatureMapper;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.enums.PostPrivacy;
import iuh.fit.postservice.domain.enums.PostStatus;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import iuh.fit.postservice.infrastructure.client.user.UserConnectionClient;
import iuh.fit.commonframework.application.dto.ApiResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import iuh.fit.postservice.domain.entities.PostMedia;
import iuh.fit.postservice.domain.enums.MediaType;
import iuh.fit.postservice.infrastructure.persistence.repository.PostMediaRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CreatePostCommandHandler {

    PostRepository postRepository;
    PostMediaRepository postMediaRepository;
    PostFeatureMapper postFeatureMapper;
    KafkaTemplate<String, Object> kafkaTemplate;
    UserConnectionClient userConnectionClient;

    @Transactional
    @CacheEvict(cacheNames = {"post-feed-v2", "post-user-feed-v2", "post-detail-v2"}, allEntries = true)
    public CreatePostResult handle(CreatePostCommand command) {
        boolean hasContent = command.getContent() != null && !command.getContent().isBlank();
        boolean hasFiles = command.getFiles() != null && !command.getFiles().isEmpty();
        boolean hasMediaUrls = command.getMediaUrls() != null && !command.getMediaUrls().isEmpty();

        if (!hasContent && !hasFiles && !hasMediaUrls) {
            throw new BusinessException(PostServiceErrorCode.INVALID_POST_CONTENT);
        }

        // A post assigned to a group must always be authored by an approved member.
        // The client-side UI is not a security boundary, so enforce this at the service layer.
        boolean requiresGroupApproval = false;
        if (command.getGroupId() != null) {
            try {
                ApiResponse<List<UserConnectionClient.GroupFeedVisibility>> response =
                        userConnectionClient.getGroupFeedVisibility(List.of(command.getGroupId()));
                UserConnectionClient.GroupFeedVisibility membership = response != null && response.getData() != null
                        ? response.getData().stream().filter(entry -> command.getGroupId().equals(entry.groupId()) && entry.member()).findFirst().orElse(null) : null;
                boolean isMember = membership != null;
                if (!isMember) throw new BusinessException(PostServiceErrorCode.UNAUTHORIZED);
                requiresGroupApproval = membership.postApprovalRequired()
                        && !"ADMIN".equals(membership.role()) && !"MODERATOR".equals(membership.role());
            } catch (BusinessException exception) {
                log.error("BusinessException checking group membership for group {}: error={}, msg={}", command.getGroupId(), exception.getErrorCode(), exception.getMessage());
                throw exception;
            } catch (Exception exception) {
                log.error("Failed to check group membership for group {}: {}", command.getGroupId(), exception.getMessage(), exception);
                throw new BusinessException(PostServiceErrorCode.UNAUTHORIZED);
            }
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

        if (requiresGroupApproval) {
            post.setStatus(PostStatus.PENDING_APPROVAL);
        } else if (hasFiles) {
            post.setStatus(PostStatus.PROCESSING);
        } else {
            post.setStatus(PostStatus.PUBLISHED);
        }

        Post savedPost = postRepository.save(post);
        List<PostMedia> savedMediaList = new ArrayList<>();

        if (hasMediaUrls) {
            int sortOrder = 0;
            for (String url : command.getMediaUrls()) {
                if (url != null && !url.isBlank()) {
                    boolean isVid = url.matches("(?i).*\\.(mp4|webm|ogg|mov|m4v|mkv)(\\?.*)?$") || url.contains("/video/") || url.contains("mediaType=VIDEO");
                    MediaType mediaType = isVid ? MediaType.VIDEO : MediaType.IMAGE;
                    String fileKey = url.contains("/") ? url.substring(url.lastIndexOf('/') + 1) : ("media-" + java.util.UUID.randomUUID());
                    if (fileKey.contains("?")) fileKey = fileKey.substring(0, fileKey.indexOf('?'));

                    PostMedia postMedia = PostMedia.builder()
                            .postId(savedPost.getId())
                            .fileUrl(url)
                            .fileKey(fileKey)
                            .mediaType(mediaType)
                            .fileSize(0)
                            .sortOrder(sortOrder++)
                            .build();

                    savedMediaList.add(postMediaRepository.save(postMedia));
                }
            }
        }

        if (hasFiles) {
            List<PostCreatedEvent.MediaPayload> mediaPayloads = new ArrayList<>();
            int sortOrder = savedMediaList.size();

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

        return postFeatureMapper.toCreateResult(savedPost, savedMediaList);
    }
}
