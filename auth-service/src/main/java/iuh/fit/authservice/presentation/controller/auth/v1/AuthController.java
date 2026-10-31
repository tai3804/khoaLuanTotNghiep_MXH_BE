package iuh.fit.authservice.presentation.controller.auth.v1;

import iuh.fit.authservice.application.features.auth.commands.login_user.LoginUserCommand;
import iuh.fit.authservice.application.features.auth.commands.login_user.LoginUserCommandHandler;
import iuh.fit.authservice.application.features.auth.commands.login_user.LoginUserResult;
import iuh.fit.authservice.application.features.auth.commands.logout_user.LogoutUserCommand;
import iuh.fit.authservice.application.features.auth.commands.logout_user.LogoutUserCommandHandler;
import iuh.fit.authservice.application.features.auth.commands.refresh_token.RefreshTokenCommand;
import iuh.fit.authservice.application.features.auth.commands.refresh_token.RefreshTokenCommandHandler;
import iuh.fit.authservice.application.features.auth.commands.refresh_token.RefreshTokenResult;
import iuh.fit.authservice.application.features.auth.commands.register_user.RegisterUserCommand;
import iuh.fit.authservice.application.features.auth.commands.register_user.RegisterUserCommandHandler;
import iuh.fit.authservice.application.features.auth.commands.register_user.RegisterUserResult;
import iuh.fit.authservice.presentation.dto.request.LoginUserRequest;
import iuh.fit.authservice.presentation.dto.request.LogoutUserRequest;
import iuh.fit.authservice.presentation.dto.request.RefreshTokenRequest;
import iuh.fit.authservice.presentation.dto.request.RegisterUserRequest;
import iuh.fit.authservice.presentation.dto.response.LoginUserResponse;
import iuh.fit.authservice.presentation.dto.response.RefreshTokenResponse;
import iuh.fit.authservice.presentation.dto.response.RegisterUserResponse;
import iuh.fit.authservice.presentation.mapper.AuthPresentationMapper;
import iuh.fit.authservice.domain.repository.UserRepository;
import iuh.fit.authservice.presentation.dto.request.SendRegisterOtpRequest;
import iuh.fit.authservice.presentation.dto.request.VerifyRegisterOtpRequest;
import iuh.fit.authservice.application.exception.AuthErrorCode;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.cache.RedisCacheService;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import iuh.fit.commonframework.infrastructure.security.OtpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import iuh.fit.authservice.presentation.constants.ApiConstants;
import iuh.fit.authservice.presentation.constants.MessageConstants;

@Slf4j
@RestController
@RequestMapping(ApiConstants.AUTH_API)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Authentication", description = "APIs for user login, register, logout, and token refresh")
public class AuthController {

    private static final int MAX_OTP_ATTEMPTS = 5;

    RegisterUserCommandHandler registerUserCommandHandler;
    LoginUserCommandHandler loginUserCommandHandler;
    LogoutUserCommandHandler logoutUserCommandHandler;
    RefreshTokenCommandHandler refreshTokenCommandHandler;
    AuthPresentationMapper authPresentationMapper;
    UserRepository userRepository;
    RedisCacheService redisCacheService;
    OtpUtil otpUtil;
    KafkaTemplate<String, Object> kafkaTemplate;
    JwtUtil jwtUtil;
    JwtDecoder jwtDecoder;

    @NonFinal
    @Value("${app.security.jwt.expiration.refresh-token}")
    long refreshTokenExpiration;

    @NonFinal
    @Value("${AUTH_OTP_COOLDOWN_SECONDS:60}")
    int otpCooldownSeconds;

    @PostMapping("/register/send-otp")
    @Operation(summary = "Send registration OTP", description = "Checks if email exists and sends OTP for email verification")
    public ResponseEntity<ApiResponse<Void>> sendRegisterOtp(@Valid @RequestBody SendRegisterOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        String cooldownKey = "register_otp_cooldown:" + email;
        try {
            if (redisCacheService.hasKey(cooldownKey)) {
                throw new BusinessException(AuthErrorCode.OTP_REQUEST_COOLDOWN);
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.warn("Redis check register cooldown error: {}", e.getMessage());
        }

        String otp = otpUtil.generateOtp();
        String redisKey = "register_email_otp:" + email;
        String attemptsKey = "register_otp_attempts:" + email;

        Duration otpTtl = Duration.ofMinutes(15);
        try {
            redisCacheService.set(redisKey, otp, otpTtl);
            redisCacheService.delete(attemptsKey);
            redisCacheService.set(cooldownKey, "1", Duration.ofSeconds(otpCooldownSeconds > 0 ? otpCooldownSeconds : 60));
        } catch (Exception e) {
            log.error("Failed to set register_email_otp in Redis: {}", e.getMessage());
            throw new BusinessException(AuthErrorCode.SERVICE_UNAVAILABLE);
        }
        log.info("Generated registration email OTP for {}", email);

        try {
            kafkaTemplate.send("notification.email.register-otp", Map.of(
                    "email", email,
                    "otp", otp,
                    "firstName", "Bạn"
            ));
        } catch (Exception e) {
            log.warn("Failed to publish registration OTP to Kafka: {}", e.getMessage());
        }

        return ResponseEntity.ok(ApiResponse.success(null, "Mã OTP xác thực đã được gửi tới email của bạn"));
    }

    @PostMapping("/register/verify-otp")
    @Operation(summary = "Verify registration OTP", description = "Verifies 6-digit OTP code sent to email and creates a registration session")
    public ResponseEntity<ApiResponse<Map<String, String>>> verifyRegisterOtp(@Valid @RequestBody VerifyRegisterOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String redisKey = "register_email_otp:" + email;
        String attemptsKey = "register_otp_attempts:" + email;

        String storedOtp;
        try {
            storedOtp = redisCacheService.get(redisKey, String.class);
        } catch (Exception e) {
            log.error("Failed to read register OTP from Redis: {}", e.getMessage());
            throw new BusinessException(AuthErrorCode.SERVICE_UNAVAILABLE);
        }

        if (storedOtp == null) {
            throw new BusinessException(AuthErrorCode.INVALID_OTP);
        }

        if (!storedOtp.equals(request.getOtpCode().trim())) {
            int attempts = incrementAttemptCounter(attemptsKey, Duration.ofMinutes(15));
            if (attempts >= MAX_OTP_ATTEMPTS) {
                redisCacheService.delete(redisKey);
                redisCacheService.delete(attemptsKey);
                throw new BusinessException(AuthErrorCode.TOO_MANY_ATTEMPTS);
            }
            throw new BusinessException(AuthErrorCode.INVALID_OTP);
        }

        // Evict OTP immediately to prevent reuse
        redisCacheService.delete(redisKey);
        redisCacheService.delete(attemptsKey);

        String registerSessionToken = UUID.randomUUID().toString();
        try {
            redisCacheService.set("register_session:" + registerSessionToken, email, Duration.ofMinutes(30));
        } catch (Exception e) {
            log.error("Failed to set register_session in Redis: {}", e.getMessage());
            throw new BusinessException(AuthErrorCode.SERVICE_UNAVAILABLE);
        }

        return ResponseEntity.ok(ApiResponse.success(Map.of("registerSessionToken", registerSessionToken), "Xác thực OTP email thành công"));
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates a new user account with email and password")
    public ResponseEntity<ApiResponse<RegisterUserResponse>> register(@Valid @RequestBody RegisterUserRequest request) {
        RegisterUserCommand command = authPresentationMapper.toCommand(request);
        RegisterUserResult result = registerUserCommandHandler.handle(command);
        RegisterUserResponse response = authPresentationMapper.toResponse(result);
        return ResponseEntity.ok(ApiResponse.success(response, MessageConstants.USER_REGISTERED_SUCCESSFULLY));
    }

    @PostMapping("/login")
    @Operation(summary = "Login user", description = "Authenticates a user and returns a JWT access token")
    public ResponseEntity<ApiResponse<LoginUserResponse>> login(
            @Valid @RequestBody LoginUserRequest request,
            @RequestHeader(value = "X-Client-Type", defaultValue = "WEB") String clientType,
            @RequestHeader(value = "X-Device-Fingerprint", required = false) String headerFingerprint,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        LoginUserCommand command = authPresentationMapper.toCommand(request);
        if (command.getDeviceFingerprint() == null || command.getDeviceFingerprint().isBlank()) {
            command.setDeviceFingerprint(
                    (headerFingerprint != null && !headerFingerprint.isBlank()) ? headerFingerprint.trim() : "default-web-device");
        }
        if (command.getDeviceName() == null || command.getDeviceName().isBlank()) {
            command.setDeviceName(httpRequest.getHeader("User-Agent"));
        }
        if (command.getIpAddress() == null || command.getIpAddress().isBlank()) {
            command.setIpAddress(extractClientIp(httpRequest));
        }

        LoginUserResult result = loginUserCommandHandler.handle(command);
        LoginUserResponse response = authPresentationMapper.toResponse(result);

        if (response.getRefreshToken() != null) {
            setRefreshTokenCookie(httpRequest, httpResponse, response.getRefreshToken(), refreshTokenExpiration / 1000);
        }

        return ResponseEntity.ok(ApiResponse.success(response, MessageConstants.USER_LOGGED_IN_SUCCESSFULLY));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Generates a new access token using a valid refresh token from Cookie or Request Body")
    public ResponseEntity<ApiResponse<RefreshTokenResponse>> refreshToken(
            @RequestBody(required = false) RefreshTokenRequest request,
            @CookieValue(name = "refreshToken", required = false) String cookieRefreshToken,
            @RequestHeader(value = "X-Client-Type", defaultValue = "WEB") String clientType,
            @RequestHeader(value = "X-Device-Fingerprint", required = false) String deviceFingerprint,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        String token = (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank())
                ? request.getRefreshToken().trim()
                : (cookieRefreshToken != null ? cookieRefreshToken.trim() : null);

        RefreshTokenCommand command = RefreshTokenCommand.builder()
                .refreshToken(token)
                .deviceFingerprint(deviceFingerprint)
                .build();

        RefreshTokenResult result = refreshTokenCommandHandler.handle(command);
        RefreshTokenResponse response = authPresentationMapper.toResponse(result);

        if (response.getRefreshToken() != null) {
            setRefreshTokenCookie(httpRequest, httpResponse, response.getRefreshToken(), refreshTokenExpiration / 1000);
        }

        return ResponseEntity.ok(ApiResponse.success(response, MessageConstants.TOKEN_REFRESHED_SUCCESSFULLY));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user", description = "Revokes the current device token and clears the refresh token cookie")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) LogoutUserRequest request,
            @CookieValue(name = "refreshToken", required = false) String cookieRefreshToken,
            @RequestHeader(value = "X-Device-Fingerprint", required = false) String headerFingerprint,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr == null && cookieRefreshToken != null && !cookieRefreshToken.isBlank()) {
            try {
                Jwt jwt = jwtDecoder.decode(cookieRefreshToken.trim());
                userIdStr = jwt.getSubject();
            } catch (Exception ignored) {
            }
        }

        String resolvedFingerprint = (request != null && request.getDeviceFingerprint() != null && !request.getDeviceFingerprint().isBlank())
                ? request.getDeviceFingerprint().trim()
                : (headerFingerprint != null && !headerFingerprint.isBlank() ? headerFingerprint.trim() : null);

        String refreshTokenHash = (cookieRefreshToken != null && !cookieRefreshToken.isBlank())
                ? jwtUtil.hashToken(cookieRefreshToken.trim())
                : null;

        if (userIdStr != null && (resolvedFingerprint != null || refreshTokenHash != null)) {
            try {
                LogoutUserCommand command = new LogoutUserCommand();
                command.setUserId(UUID.fromString(userIdStr));
                command.setDeviceFingerprint(resolvedFingerprint);
                command.setRefreshTokenHash(refreshTokenHash);
                logoutUserCommandHandler.handle(command);
            } catch (Exception e) {
                log.warn("Failed to revoke device session on logout: {}", e.getMessage());
            }
        }

        // Always clear HttpOnly refreshToken cookie
        setRefreshTokenCookie(httpRequest, httpResponse, "", 0);

        return ResponseEntity.ok(ApiResponse.success(null, MessageConstants.USER_LOGGED_OUT_SUCCESSFULLY));
    }

    private void setRefreshTokenCookie(HttpServletRequest httpRequest, HttpServletResponse httpResponse, String token, long maxAgeSeconds) {
        boolean isHttps = (httpRequest != null && httpRequest.isSecure())
                || (httpRequest != null && "https".equalsIgnoreCase(httpRequest.getHeader("X-Forwarded-Proto")));
        ResponseCookie springCookie = ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(isHttps)
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite(isHttps ? "None" : "Lax")
                .build();
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, springCookie.toString());
    }

    private int incrementAttemptCounter(String key, Duration ttl) {
        try {
            Integer current = redisCacheService.get(key, Integer.class);
            int next = (current != null ? current : 0) + 1;
            redisCacheService.set(key, next, ttl);
            return next;
        } catch (Exception e) {
            return 1;
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
