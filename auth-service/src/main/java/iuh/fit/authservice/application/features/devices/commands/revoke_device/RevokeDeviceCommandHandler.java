package iuh.fit.authservice.application.features.devices.commands.revoke_device;

import iuh.fit.authservice.domain.entities.Device;
import iuh.fit.authservice.domain.enums.DeviceStatus;
import iuh.fit.authservice.domain.repository.DeviceRepository;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.application.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RevokeDeviceCommandHandler {

    DeviceRepository deviceRepository;

    @Transactional
    public void handle(RevokeDeviceCommand command) {
        Device device = deviceRepository.findById(command.getDeviceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!device.getUser().getId().equals(command.getUserId())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        device.setStatus(DeviceStatus.REVOKED);
        device.setRefreshTokenHash(null);
        
        deviceRepository.save(device);
    }
}
