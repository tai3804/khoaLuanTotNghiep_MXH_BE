 package iuh.fit.commonframework.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostMediaUploadedEvent implements Serializable {
    UUID postId;
    boolean success;
    List<MediaItemPayload> mediaList;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class MediaItemPayload implements Serializable {
        String fileUrl;
        String fileKey;
        String mediaType;
        long fileSize;
        int sortOrder;
    }
}
