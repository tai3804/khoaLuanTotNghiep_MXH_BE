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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MfaSetupCommandHandler {

    UserRepository userRepository;
    TotpUtil totpUtil;
    RedisCacheService redisCacheService;
    MfaApplicationMapper mfaApplicationMapper;

    @Transactional
    public MfaSetupResult handle(MfaSetupCommand command) {
        User user = userRepository.findById(command.getUserId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));

        String secretKey = totpUtil.generateSecretKey();
        
        String userEmail = user.getEmail();
        if (userEmail == null || userEmail.isBlank()) {
            userEmail = "user@social.net";
        }

        String qrCodeUrl = totpUtil.getQrCodeUrl(userEmail, secretKey, "SocialNetwork");

        // Save secretKey in DB entity temporarily as fallback only if 2FA is not enabled yet
        if (!user.isMfaEnabled()) {
            user.setMfaSecret(secretKey);
            userRepository.save(user);
        }

        try {
            redisCacheService.set("mfa_setup_secret:" + user.getId(), secretKey, Duration.ofMinutes(10));
        } catch (Exception e) {
            log.warn("Could not cache MFA setup secret in Redis: {}", e.getMessage());
        }

        return mfaApplicationMapper.toMfaSetupResult(secretKey, qrCodeUrl);
    }
}
