package iuh.fit.authservice.application.features.auth.commands.mfa_setup;

import iuh.fit.authservice.application.exception.AuthErrorCode;
import iuh.fit.authservice.application.mapper.MfaApplicationMapper;
import iuh.fit.authservice.domain.entities.User;
import iuh.fit.authservice.domain.repository.UserRepository;
import iuh.fit.authservice.infrastructure.security.TotpUtil;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.cache.RedisCacheService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MfaSetupCommandHandler {

    UserRepository userRepository;
    TotpUtil totpUtil;
    RedisCacheService redisCacheService;
    MfaApplicationMapper mfaApplicationMapper;

    public MfaSetupResult handle(MfaSetupCommand command) {
        User user = userRepository.findById(command.getUserId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));

        String secretKey = totpUtil.generateSecretKey();
        String qrCodeUrl = totpUtil.getQrCodeUrl(user.getEmail(), secretKey, null);

        redisCacheService.set("mfa_setup_secret:" + user.getId(), secretKey, Duration.ofMinutes(10));

        return mfaApplicationMapper.toMfaSetupResult(secretKey, qrCodeUrl);
    }
}
