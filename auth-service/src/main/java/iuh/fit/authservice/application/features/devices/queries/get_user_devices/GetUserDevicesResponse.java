package iuh.fit.authservice.application.features.devices.queries.get_user_devices;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GetUserDevicesResponse {
    
    List<DeviceResponse> devices;
}
