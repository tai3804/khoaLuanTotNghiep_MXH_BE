package iuh.fit.authservice.application.features.auth.commands.mfa_disable;

import iuh.fit.authservice.application.exception.AuthErrorCode;
import iuh.fit.authservice.domain.entities.User;
import iuh.fit.authservice.domain.enums.MfaType;
import iuh.fit.authservice.domain.repository.UserRepository;
import iuh.fit.authservice.infrastructure.security.TotpUtil;
import iuh.fit.commonframework.application.exception.BusinessException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MfaDisableCommandHandler {

    UserRepository userRepository;
    TotpUtil totpUtil;

    public void handle(MfaDisableCommand command) {
        User user = userRepository.findById(command.getUserId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));

        if (MfaType.TOTP == user.getMfaType()) {
            if (!totpUtil.verifyCode(user.getMfaSecret(), command.getOtpCode())) {
                throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
            }
        }

        user.setMfaEnabled(false);
        user.setMfaType(MfaType.NONE);
        user.setMfaSecret(null);
        userRepository.save(user);
    }
}
