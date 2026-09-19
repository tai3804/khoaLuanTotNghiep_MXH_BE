package iuh.fit.authservice.application.features.auth.commands.mfa_enable;

import iuh.fit.authservice.application.exception.AuthErrorCode;
import iuh.fit.authservice.domain.entities.User;
import iuh.fit.authservice.domain.enums.MfaType;
import iuh.fit.authservice.domain.repository.UserRepository;
import iuh.fit.authservice.infrastructure.security.TotpUtil;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.cache.RedisCacheService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MfaEnableCommandHandler {

    UserRepository userRepository;
    TotpUtil totpUtil;
    RedisCacheService redisCacheService;

    @Transactional
    public void handle(MfaEnableCommand command) {
        if (command.getMfaType() == null || command.getMfaType() == MfaType.NONE) {
            throw new BusinessException(AuthErrorCode.INVALID_MFA_TYPE);
        }

        User user = userRepository.findById(command.getUserId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));

        MfaType mfaType = command.getMfaType();

        if (MfaType.TOTP == mfaType) {
            String secretKey = null;
            try {
                secretKey = redisCacheService.get("mfa_setup_secret:" + user.getId(), String.class);
            } catch (Exception e) {
                log.warn("Failed to read mfa_setup_secret from Redis: {}", e.getMessage());
            }

            if (secretKey == null || secretKey.isBlank()) {
                secretKey = user.getMfaSecret();
            }

            if (secretKey == null || secretKey.isBlank()) {
                throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
            }

            if (!totpUtil.verifyCode(secretKey, command.getOtpCode())) {
                throw new BusinessException(AuthErrorCode.INVALID_OTP);
            }

            user.setMfaSecret(secretKey);
            user.setMfaEnabled(true);
            user.setMfaType(MfaType.TOTP);
            userRepository.save(user);

            try {
                redisCacheService.delete("mfa_setup_secret:" + user.getId());
            } catch (Exception ignored) {}
        } else if (MfaType.EMAIL == mfaType) {
            user.setMfaEnabled(true);
            user.setMfaType(MfaType.EMAIL);
            userRepository.save(user);
        } else {
            throw new BusinessException(AuthErrorCode.INVALID_MFA_TYPE);
        }
    }
}
