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
public class ReportCreatedEvent implements Serializable {
    UUID reportId;
    UUID targetId;
    String targetType;
    UUID reporterId;
    String reason;
}
