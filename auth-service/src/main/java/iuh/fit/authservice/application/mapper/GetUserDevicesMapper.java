package iuh.fit.authservice.application.mapper;

import iuh.fit.authservice.application.features.devices.queries.get_user_devices.DeviceResponse;
import iuh.fit.authservice.application.features.devices.queries.get_user_devices.GetUserDevicesResponse;
import iuh.fit.authservice.domain.entities.Device;
import iuh.fit.commonframework.application.mapper.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface GetUserDevicesMapper extends BaseMapper<Device, DeviceResponse> {

    default GetUserDevicesResponse toResponse(List<Device> devices) {
        if (devices == null) {
            return null;
        }
        return GetUserDevicesResponse.builder()
                .devices(toDto(devices))
                .build();
    }
}
