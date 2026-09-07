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
public class PostCreatedEvent implements Serializable {
    UUID postId;
    UUID authorId;
    List<MediaPayload> files;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class MediaPayload implements Serializable {
        String fileName;
        String contentType;
        byte[] data;
        int sortOrder;
    }
}
