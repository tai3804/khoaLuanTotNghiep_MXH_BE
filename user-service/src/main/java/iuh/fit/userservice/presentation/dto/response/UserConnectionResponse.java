package iuh.fit.userservice.presentation.dto.response;

import iuh.fit.userservice.domain.enums.ConnectionStatus;
import iuh.fit.userservice.domain.enums.ConnectionType;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserConnectionResponse {
    UUID id;
    UUID requesterId;
    UUID targetId;
    ConnectionType type;
    ConnectionStatus status;
    LocalDateTime createdAt;
}
