package iuh.fit.authservice.application.features.devices.queries.get_user_devices;

import iuh.fit.authservice.domain.enums.DeviceStatus;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeviceResponse {
    UUID id;
    String deviceName;
    String deviceFingerprint;
    String ipAddress;
    String location;
    LocalDateTime lastActiveAt;
    DeviceStatus status;
    LocalDateTime createdAt;
}
