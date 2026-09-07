package iuh.fit.commonframework.event;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostModeratedEvent implements Serializable {
    UUID postId;
    String action;
    String reason;
    UUID moderatorId;
}
