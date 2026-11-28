package iuh.fit.authservice.application.features.auth.commands.logout_user;

import iuh.fit.authservice.domain.entities.Device;
import iuh.fit.authservice.domain.enums.DeviceStatus;
import iuh.fit.authservice.domain.repository.DeviceRepository;
import iuh.fit.commonframework.infrastructure.cache.RedisCacheService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LogoutUserCommandHandler {

    DeviceRepository deviceRepository;
    RedisCacheService redisCacheService;

    @NonFinal
    @Value("${app.security.jwt.expiration.access-token:3600000}")
    long accessTokenExpirationMs;

    @Transactional
    public void handle(LogoutUserCommand command) {
        if (command.getUserId() == null) {
            return;
        }

        Optional<Device> deviceOpt = Optional.empty();
        if (command.getDeviceFingerprint() != null && !command.getDeviceFingerprint().isBlank()) {
            deviceOpt = deviceRepository.findByUserIdAndDeviceFingerprint(
                    command.getUserId(), command.getDeviceFingerprint().trim());
        }
        if (deviceOpt.isEmpty() && command.getRefreshTokenHash() != null && !command.getRefreshTokenHash().isBlank()) {
            deviceOpt = deviceRepository.findByUserIdAndRefreshTokenHash(
                    command.getUserId(), command.getRefreshTokenHash().trim());
        }

        deviceOpt.ifPresent(device -> {
            device.setStatus(DeviceStatus.REVOKED);
            device.setRefreshTokenHash(null);
            deviceRepository.save(device);
            redisCacheService.revokeDevice(device.getId(), Duration.ofMillis(accessTokenExpirationMs).plusMinutes(1));
        });
    }
}
