package iuh.fit.authservice.application.features.devices.queries.get_user_devices;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetUserDevicesQuery {
    UUID userId;
}
