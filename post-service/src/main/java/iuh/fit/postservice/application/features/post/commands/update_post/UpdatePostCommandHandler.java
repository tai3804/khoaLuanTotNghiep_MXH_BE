package iuh.fit.postservice.application.features.post.commands.update_post;

import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.postservice.application.exception.PostServiceErrorCode;
import iuh.fit.postservice.application.mapper.PostFeatureMapper;
import iuh.fit.postservice.domain.entities.Post;
import iuh.fit.postservice.domain.entities.PostMedia;
import iuh.fit.postservice.domain.enums.MediaType;
import iuh.fit.postservice.domain.enums.PostPrivacy;
import iuh.fit.postservice.infrastructure.persistence.repository.PostMediaRepository;
import iuh.fit.postservice.infrastructure.persistence.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UpdatePostCommandHandler {

    PostRepository postRepository;
    PostMediaRepository postMediaRepository;
    PostFeatureMapper postFeatureMapper;

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

        // The edit form submits the complete attachment list. This makes add and
        // remove operations deterministic, while a missing field leaves media intact
        // for other update endpoints (pin/archive/privacy).
        if (command.getMediaUrls() != null) {
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
        String path = url.split("[?#]", 2)[0];
        int lastSlash = path.lastIndexOf('/');
        String name = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        if (name.isBlank()) {
            name = "edited-media-" + sortOrder;
        }
        // file_key is an internal reference here; URLs may be signed or long.
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }
}
