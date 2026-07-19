package iuh.fit.authservice.application.features.devices.commands.revoke_device;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RevokeDeviceCommand {
    UUID userId;
    UUID deviceId;
}
