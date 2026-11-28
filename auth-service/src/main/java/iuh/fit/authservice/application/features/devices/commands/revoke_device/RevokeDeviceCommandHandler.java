package iuh.fit.authservice.application.features.devices.commands.revoke_device;

import iuh.fit.authservice.domain.entities.Device;
import iuh.fit.authservice.domain.enums.DeviceStatus;
import iuh.fit.authservice.domain.repository.DeviceRepository;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.application.exception.ErrorCode;
import iuh.fit.commonframework.infrastructure.cache.RedisCacheService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RevokeDeviceCommandHandler {

    DeviceRepository deviceRepository;
    RedisCacheService redisCacheService;

    @NonFinal
    @Value("${app.security.jwt.expiration.access-token:3600000}")
    long accessTokenExpirationMs;

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
        // Access token còn hạn của thiết bị này bị từ chối ngay ở yêu cầu kế tiếp
        redisCacheService.revokeDevice(device.getId(), Duration.ofMillis(accessTokenExpirationMs).plusMinutes(1));
    }
}
