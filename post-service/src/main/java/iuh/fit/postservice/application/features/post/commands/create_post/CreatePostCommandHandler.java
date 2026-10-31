package iuh.fit.postservice.application.features.post.commands.create_post;

import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.event.PostCreatedEvent;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.application.mapper.PostFeatureMapper;
import iuh.fit.postservice.application.service.GroupNotificationPublisher;
import iuh.fit.postservice.application.service.PostPublicationHelper;
import iuh.fit.postservice.application.service.ReputationGuard;
import iuh.fit.postservice.application.service.ranking.RankedFeedCache;
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
import java.util.HashSet;
import java.util.List;
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
    RankedFeedCache rankedFeedCache;
    ReputationGuard reputationGuard;
    GroupNotificationPublisher groupNotificationPublisher;
    PostPublicationHelper postPublicationHelper;

    @Transactional
    @CacheEvict(cacheNames = {"post-feed-v2", "post-user-feed-v2", "post-detail-v2"}, allEntries = true)
    public CreatePostResult handle(CreatePostCommand command) {
        boolean hasContent = command.getContent() != null && !command.getContent().isBlank();
        boolean hasFiles = command.getFiles() != null && !command.getFiles().isEmpty();
        boolean hasMediaUrls = command.getMediaUrls() != null && !command.getMediaUrls().isEmpty();
        boolean hasPoll = (command.getPoll() != null && command.getPoll().getQuestion() != null && !command.getPoll().getQuestion().isBlank())
                || (command.getPollJson() != null && !command.getPollJson().isBlank());

        reputationGuard.checkCanPost(command.getAuthorId(),
                command.getContent() != null && command.getContent().startsWith(ReputationGuard.LIVE_MARKER));

        if (!hasContent && !hasFiles && !hasMediaUrls && !hasPoll) {
            throw new BusinessException(PostServiceErrorCode.INVALID_POST_CONTENT);
        }

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
                    throw new BusinessException(PostServiceErrorCode.GROUP_MEMBERSHIP_REQUIRED);
                }
                requiresGroupApproval = membership.postApprovalRequired()
                        && !"ADMIN".equals(membership.role()) && !"MODERATOR".equals(membership.role());
            } catch (BusinessException exception) {
                log.error("BusinessException checking group membership for group {}: error={}, msg={}", command.getGroupId(), exception.getErrorCode(), exception.getMessage());
                throw exception;
            } catch (Exception exception) {
                log.error("Failed to check group membership for group {}: {}", command.getGroupId(), exception.getMessage(), exception);
                throw new BusinessException(PostServiceErrorCode.GROUP_SERVICE_UNAVAILABLE);
            }
        }

        Post post = postFeatureMapper.toEntity(command);
        if (post.getGroupId() != null || post.getPrivacy() == null) {
            post.setPrivacy(PostPrivacy.PUBLIC);
        }

        if (post.getPrivacy() != PostPrivacy.CUSTOM) {
            post.setAllowedUserIds(new HashSet<>());
        } else if (post.getAllowedUserIds() == null) {
            post.setAllowedUserIds(new HashSet<>());
        }

        Set<String> hashtags = TagAndHashtagHelper.extractHashtags(command.getContent());
        post.setHashtags(hashtags);

        Set<UUID> allTaggedUserIds = new HashSet<>();
        if (command.getTaggedUserIds() != null) {
            allTaggedUserIds.addAll(command.getTaggedUserIds());
        }
        allTaggedUserIds.addAll(TagAndHashtagHelper.extractMentions(command.getContent()));
        post.setTaggedUserIds(allTaggedUserIds);

        if (hasPoll) {
            if (command.getPoll() != null) {
                post.setPollData(iuh.fit.postservice.application.util.PostPollHelper.buildPollJson(command.getPoll()));
            } else if (command.getPollJson() != null && !command.getPollJson().isBlank()) {
                post.setPollData(command.getPollJson().trim());
            }
        }

        if (command.getScheduledPublishAt() != null && command.getScheduledPublishAt().isAfter(java.time.Instant.now())) {
            post.setScheduledPublishAt(command.getScheduledPublishAt());
        }

        if (requiresGroupApproval) {
            post.setStatus(PostStatus.PENDING_APPROVAL);
        } else if (hasFiles) {
            post.setStatus(PostStatus.PROCESSING);
        } else if (post.getScheduledPublishAt() != null) {
            post.setStatus(PostStatus.SCHEDULED);
        } else {
            post.setStatus(PostStatus.PUBLISHED);
        }

        post.setLastActivityAt(java.time.LocalDateTime.now());
        Post savedPost = postRepository.save(post);
        rankedFeedCache.evictViewer(savedPost.getAuthorId());
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

        // Chỉ phát PostCreatedEvent ngay khi cần upload file lên S3 hoặc khi bài được PUBLISHED ngay lập tức.
        // Bài SCHEDULED / PENDING_APPROVAL không kèm file sẽ phát sự kiện này vào đúng thời điểm được đăng/duyệt.
        if (hasFiles || savedPost.getStatus() == PostStatus.PUBLISHED) {
            PostCreatedEvent event = PostCreatedEvent.builder()
                    .postId(savedPost.getId())
                    .authorId(savedPost.getAuthorId())
                    .content(savedPost.getContent())
                    .files(mediaPayloads)
                    .build();

            UUID postId = savedPost.getId();
            boolean trackMediaUpload = hasFiles;
            kafkaTemplate.send("post.created", event).whenComplete((sendResult, ex) -> {
                if (ex != null && trackMediaUpload) {
                    postPublicationHelper.markMediaUploadFailed(postId, ex);
                }
            });
            log.info("Published PostCreatedEvent to Kafka topic 'post.created' for postId: {} (status={})",
                    savedPost.getId(), savedPost.getStatus());
        }

        if (savedPost.getStatus() == PostStatus.PENDING_APPROVAL && savedPost.getGroupId() != null) {
            groupNotificationPublisher.postPending(savedPost);
        } else if (savedPost.getStatus() == PostStatus.PUBLISHED) {
            postPublicationHelper.notifyPostPublished(savedPost);
        }

        CreatePostResult result = postFeatureMapper.toCreateResult(savedPost, savedMediaList);
        result.setPoll(iuh.fit.postservice.application.util.PostPollHelper.toResponse(savedPost.getPollData(), savedPost.getAuthorId()));
        return result;
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
