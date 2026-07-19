package iuh.fit.authservice.application.features.devices.queries.get_user_devices;

import iuh.fit.authservice.domain.entities.Device;
import iuh.fit.authservice.domain.repository.DeviceRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import iuh.fit.authservice.application.mapper.GetUserDevicesMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetUserDevicesQueryHandler {

    DeviceRepository deviceRepository;
    GetUserDevicesMapper getUserDevicesMapper;

    public GetUserDevicesResponse handle(GetUserDevicesQuery query) {
        List<Device> devices = deviceRepository.findByUserId(query.getUserId());
        return getUserDevicesMapper.toResponse(devices);
    }
}
