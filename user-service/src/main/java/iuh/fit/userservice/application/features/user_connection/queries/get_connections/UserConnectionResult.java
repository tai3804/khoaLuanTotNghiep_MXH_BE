package iuh.fit.userservice.application.features.user_connection.queries.get_connections;

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
public class UserConnectionResult {
    UUID id;
    UUID requesterId;
    UUID targetId;
    ConnectionType type;
    ConnectionStatus status;
    LocalDateTime createdAt;
}
