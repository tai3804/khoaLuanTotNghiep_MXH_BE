package iuh.fit.postservice.application.features.post.commands.create_post;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.event.PostCreatedEvent;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.application.mapper.PostFeatureMapper;
import iuh.fit.postservice.application.util.TagAndHashtagHelper;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.entities.PostMedia;
import iuh.fit.postservice.domain.enums.MediaType;
import iuh.fit.postservice.domain.enums.PostPrivacy;
import iuh.fit.postservice.domain.enums.PostStatus;
import iuh.fit.postservice.infrastructure.client.user.UserConnectionClient;
import iuh.fit.postservice.infrastructure.persistence.repository.PostMediaRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
                if (!isMember) {
                    log.warn("User {} is not an approved member of group {}", command.getAuthorId(), command.getGroupId());
                    throw new BusinessException(PostServiceErrorCode.UNAUTHORIZED);
                }
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
        if (post.getGroupId() != null || post.getPrivacy() == null) {
            post.setPrivacy(PostPrivacy.PUBLIC);
        }

        // Only list allowedUserIds when privacy is CUSTOM
        if (post.getPrivacy() != PostPrivacy.CUSTOM) {
            post.setAllowedUserIds(new HashSet<>());
        } else if (post.getAllowedUserIds() == null) {
            post.setAllowedUserIds(new HashSet<>());
        }

        // Extract and assign hashtags
        Set<String> hashtags = TagAndHashtagHelper.extractHashtags(command.getContent());
        post.setHashtags(hashtags);

        // Extract and assign tagged users
        Set<UUID> allTaggedUserIds = new HashSet<>();
        if (command.getTaggedUserIds() != null) {
            allTaggedUserIds.addAll(command.getTaggedUserIds());
        }
        allTaggedUserIds.addAll(TagAndHashtagHelper.extractMentions(command.getContent()));
        post.setTaggedUserIds(allTaggedUserIds);

        if (requiresGroupApproval) {
            post.setStatus(PostStatus.PENDING_APPROVAL);
        } else if (command.getScheduledPublishAt() != null && command.getScheduledPublishAt().isAfter(java.time.Instant.now())) {
            post.setStatus(PostStatus.SCHEDULED);
            post.setScheduledPublishAt(command.getScheduledPublishAt());
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
                    String fileKey = extractFileKey(url);

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

        List<PostCreatedEvent.MediaPayload> mediaPayloads = new ArrayList<>();
        if (hasFiles) {
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
        }

        PostCreatedEvent event = PostCreatedEvent.builder()
                .postId(savedPost.getId())
                .authorId(savedPost.getAuthorId())
                .content(savedPost.getContent())
                .files(mediaPayloads)
                .build();

        kafkaTemplate.send("post.created", event);
        log.info("Published PostCreatedEvent to Kafka topic 'post.created' for postId: {}", savedPost.getId());

        // Publish TAG_POST notifications to tagged users
        if (!allTaggedUserIds.isEmpty()) {
            for (UUID taggedUserId : allTaggedUserIds) {
                if (taggedUserId != null && !taggedUserId.equals(savedPost.getAuthorId())) {
                    try {
                        Map<String, Object> tagEvent = new HashMap<>();
                        tagEvent.put("recipientId", taggedUserId.toString());
                        tagEvent.put("actorId", savedPost.getAuthorId().toString());
                        tagEvent.put("type", "TAG_POST");
                        tagEvent.put("title", "Gắn thẻ trong bài viết");
                        tagEvent.put("content", "Một người dùng đã gắn thẻ bạn trong một bài viết.");
                        tagEvent.put("targetId", savedPost.getId().toString());
                        tagEvent.put("targetUrl", "/posts/" + savedPost.getId());
                        tagEvent.put("avatarUrl", null);

                        kafkaTemplate.send("notification.in-app.send", tagEvent);
                        log.info("Published TAG_POST notification to tagged user: {}", taggedUserId);
                    } catch (Exception e) {
                        log.warn("Failed to publish TAG_POST notification for user {}: {}", taggedUserId, e.getMessage());
                    }
                }
            }
        }

        return postFeatureMapper.toCreateResult(savedPost, savedMediaList);
    }

    private String extractFileKey(String url) {
        if (url == null || url.isBlank()) return "posts/media-" + UUID.randomUUID();
        try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                URI uri = new URI(url);
                String path = uri.getPath();
                if (path != null && path.startsWith("/")) {
                    path = path.substring(1);
                }
                if (path != null && path.startsWith("api/v1/media/files/")) {
                    return path.substring("api/v1/media/files/".length());
                }
                if (path != null && !path.isBlank()) {
                    return path;
                }
            }
        } catch (Exception ignored) {}
        String clean = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
        if (clean.contains(".net/")) {
            return clean.substring(clean.indexOf(".net/") + 5);
        }
        if (clean.contains(".com/")) {
            return clean.substring(clean.indexOf(".com/") + 5);
        }
        return clean.startsWith("posts/") ? clean : ("posts/" + (clean.contains("/") ? clean.substring(clean.lastIndexOf('/') + 1) : clean));
    }
}
