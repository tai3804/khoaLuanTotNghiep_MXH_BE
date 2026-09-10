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
public class UserAvatarUpdatedEvent implements Serializable {
    UUID userId;
    String oldAvatarUrl;
    String newAvatarUrl;
    String oldCoverUrl;
    String newCoverUrl;
}
