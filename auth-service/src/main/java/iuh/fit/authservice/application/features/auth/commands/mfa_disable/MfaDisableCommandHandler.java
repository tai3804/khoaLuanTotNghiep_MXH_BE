package iuh.fit.authservice.application.features.auth.commands.mfa_disable;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MfaDisableCommandHandler {

    UserRepository userRepository;
    TotpUtil totpUtil;
    PasswordEncoder passwordEncoder;
    RedisCacheService redisCacheService;

    @Transactional
    public void handle(MfaDisableCommand command) {
        User user = userRepository.findById(command.getUserId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));

        if (!user.isMfaEnabled()) {
            return;
        }

        String providedCode = command.getOtpCode() != null ? command.getOtpCode().trim() : "";
        if (providedCode.isEmpty()) {
            throw new BusinessException(AuthErrorCode.INVALID_OTP);
        }

        if (MfaType.TOTP == user.getMfaType()) {
            if (!totpUtil.verifyCode(user.getMfaSecret(), providedCode)) {
                throw new BusinessException(AuthErrorCode.INVALID_OTP);
            }
        } else if (MfaType.EMAIL == user.getMfaType()) {
            String storedOtp = redisCacheService.get("mfa_disable_otp:" + user.getId(), String.class);
            boolean validEmailOtp = storedOtp != null && storedOtp.equals(providedCode);
            boolean validPasswordFallback = passwordEncoder.matches(providedCode, user.getPassword());
            if (!validEmailOtp && !validPasswordFallback) {
                throw new BusinessException(AuthErrorCode.INVALID_OTP);
            }
            if (validEmailOtp) {
                redisCacheService.delete("mfa_disable_otp:" + user.getId());
            }
        } else {
            throw new BusinessException(AuthErrorCode.INVALID_MFA_TYPE);
        }

        user.setMfaEnabled(false);
        user.setMfaType(MfaType.NONE);
        user.setMfaSecret(null);
        userRepository.save(user);
    }
}
