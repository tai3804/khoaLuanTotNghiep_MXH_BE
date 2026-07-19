package iuh.fit.authservice.application.features.auth.commands.logout_user;

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
public class LogoutUserCommandHandler {

    DeviceRepository deviceRepository;

    @Transactional
    public void handle(LogoutUserCommand command) {
        Device device = deviceRepository.findByUserIdAndDeviceFingerprint(command.getUserId(), command.getDeviceFingerprint())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        device.setStatus(DeviceStatus.REVOKED);
        device.setRefreshTokenHash(null); // Invalidate refresh token
        
        deviceRepository.save(device);
    }
}
