package iuh.fit.userservice.application.features.user_connection.queries.get_connections;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetConnectionsQuery {
    UUID userId;
    String mode;
    int page;
    int size;
}
