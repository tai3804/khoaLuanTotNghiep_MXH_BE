package iuh.fit.postservice.application.features.post.commands.update_post;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.application.mapper.PostFeatureMapper;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.entities.PostMedia;
import iuh.fit.postservice.domain.enums.MediaType;
import iuh.fit.postservice.domain.enums.PostPrivacy;
import iuh.fit.postservice.infrastructure.client.media.MediaClient;
import iuh.fit.postservice.infrastructure.persistence.repository.PostMediaRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UpdatePostCommandHandler {

    PostRepository postRepository;
    PostMediaRepository postMediaRepository;
    PostFeatureMapper postFeatureMapper;
    MediaClient mediaClient;

    @Transactional
    @CacheEvict(cacheNames = {"post-feed-v2", "post-user-feed-v2", "post-detail-v2"}, allEntries = true)
    public UpdatePostResult handle(UpdatePostCommand command) {
        Post post = postRepository.findByIdAndDeletedFalse(command.getPostId())
                .orElseThrow(() -> new BusinessException(PostServiceErrorCode.POST_NOT_FOUND));

        if (!post.getAuthorId().equals(command.getUserId())) {
            throw new BusinessException(PostServiceErrorCode.UNAUTHORIZED_ACTION);
        }

        if (command.getContent() != null) {
            post.setContent(command.getContent());
        }
        if (command.getPrivacy() != null) {
            post.setPrivacy(command.getPrivacy());
            if (command.getPrivacy() != PostPrivacy.CUSTOM) {
                post.setAllowedUserIds(new HashSet<>());
            } else if (command.getAllowedUserIds() != null) {
                post.setAllowedUserIds(command.getAllowedUserIds());
            }
        } else if (post.getPrivacy() == PostPrivacy.CUSTOM && command.getAllowedUserIds() != null) {
            post.setAllowedUserIds(command.getAllowedUserIds());
        }

        if (command.getIsPinned() != null) {
            post.setPinned(command.getIsPinned());
        }
        if (command.getIsArchived() != null) {
            post.setArchived(command.getIsArchived());
        }

        // The edit form submits the complete attachment list.
        if (command.getMediaUrls() != null) {
            List<PostMedia> currentMedia = postMediaRepository.findByPostIdOrderBySortOrderAsc(post.getId());
            Set<String> newUrls = command.getMediaUrls().stream()
                    .filter(u -> u != null && !u.isBlank())
                    .map(String::trim)
                    .collect(Collectors.toSet());

            // Delete removed media from S3
            for (PostMedia oldMedia : currentMedia) {
                if (!newUrls.contains(oldMedia.getFileUrl())) {
                    try {
                        String targetKey = (oldMedia.getFileUrl() != null && !oldMedia.getFileUrl().isBlank())
                                ? oldMedia.getFileUrl()
                                : oldMedia.getFileKey();
                        if (targetKey != null && !targetKey.isBlank()) {
                            mediaClient.deleteFile(targetKey);
                            log.info("Deleted removed post media from S3: {}", targetKey);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to delete removed post media [{}] from S3: {}", oldMedia.getFileKey(), e.getMessage());
                    }
                }
            }

            postMediaRepository.deleteByPostId(post.getId());

            List<PostMedia> replacementMedia = new ArrayList<>();
            int sortOrder = 0;
            for (String mediaUrl : command.getMediaUrls()) {
                if (mediaUrl == null || mediaUrl.isBlank()) {
                    continue;
                }

                String url = mediaUrl.trim();
                replacementMedia.add(PostMedia.builder()
                        .postId(post.getId())
                        .fileUrl(url)
                        .fileKey(createFileKey(url, sortOrder))
                        .mediaType(isVideo(url) ? MediaType.VIDEO : MediaType.IMAGE)
                        .fileSize(0L)
                        .sortOrder(sortOrder++)
                        .build());
            }
            if (!replacementMedia.isEmpty()) {
                postMediaRepository.saveAll(replacementMedia);
            }
        }

        Post updatedPost = postRepository.save(post);
        List<PostMedia> mediaList = postMediaRepository.findByPostIdOrderBySortOrderAsc(updatedPost.getId());

        return postFeatureMapper.toUpdateResult(updatedPost, mediaList);
    }

    private boolean isVideo(String url) {
        String path = url.toLowerCase().split("[?#]", 2)[0];
        return path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".ogg")
                || path.endsWith(".mov") || path.endsWith(".m4v");
    }

    private String createFileKey(String url, int sortOrder) {
        if (url == null || url.isBlank()) return "posts/edited-media-" + sortOrder;
        try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                java.net.URI uri = new java.net.URI(url);
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
