package iuh.fit.authservice.domain.entities;

import iuh.fit.authservice.domain.enums.DeviceStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Device {
    UUID id;
    User user;
    String deviceFingerprint;
    String deviceName;
    String ipAddress;
    String location;
    LocalDateTime lastActiveAt;
    DeviceStatus status;
    String refreshTokenHash;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    boolean deleted;
}
