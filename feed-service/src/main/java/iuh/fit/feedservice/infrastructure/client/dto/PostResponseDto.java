package iuh.fit.feedservice.infrastructure.client.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostResponseDto {
    UUID id;
    UUID authorId;
    String content;
    String privacy;
    String status;
    Set<UUID> allowedUserIds;
    UUID originalPostId;
    long likeCount;
    long commentCount;
    long shareCount;
    List<PostMediaResponseDto> mediaList;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class PostMediaResponseDto {
        UUID id;
        String mediaUrl;
        String mediaType;
        int sortOrder;
    }
}
