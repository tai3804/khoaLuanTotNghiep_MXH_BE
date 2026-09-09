package iuh.fit.userservice.presentation.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BatchPresenceRequest {

    @NotEmpty(message = "User IDs list cannot be empty")
    List<UUID> userIds;
}
