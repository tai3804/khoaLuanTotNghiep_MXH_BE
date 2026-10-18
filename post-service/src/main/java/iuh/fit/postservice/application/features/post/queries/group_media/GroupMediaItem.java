package iuh.fit.postservice.application.features.post.queries.group_media;

import iuh.fit.postservice.domain.enums.MediaType;

import java.time.LocalDateTime;
import java.util.UUID;

/** Một ảnh hoặc video trong tab "Ảnh & video" của nhóm; postId để mở bài viết gốc. */
public record GroupMediaItem(UUID id, UUID postId, String url, MediaType type, UUID authorId, LocalDateTime createdAt) { }
