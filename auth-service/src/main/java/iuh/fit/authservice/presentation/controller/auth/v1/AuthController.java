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
import jakarta.servlet.http.HttpServletResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

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

    @NonFinal
    @Value("${app.security.jwt.expiration.refresh-token}")
    long refreshTokenExpiration;

    @PostMapping("/register/send-otp")
    @Operation(summary = "Send registration OTP", description = "Checks if email exists and sends OTP for email verification")
    public ResponseEntity<ApiResponse<Void>> sendRegisterOtp(@Valid @RequestBody SendRegisterOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        String otp = otpUtil.generateOtp();
        String redisKey = "register_email_otp:" + email;

        // TTL of 15 minutes (900 seconds) for OTP
        try {
            redisCacheService.set(redisKey, otp, java.time.Duration.ofMinutes(15));
        } catch (Exception e) {
            log.warn("Failed to set register_email_otp in Redis: {}", e.getMessage());
        }
        log.info("Generated Registration Email OTP for email {}: {}", email, otp);

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

        String storedOtp = null;
        try {
            storedOtp = redisCacheService.get(redisKey, String.class);
        } catch (Exception e) {
            log.error("Failed to read register OTP from Redis: {}", e.getMessage());
        }

        if (storedOtp == null || !storedOtp.equals(request.getOtpCode().trim())) {
            throw new BusinessException(AuthErrorCode.INVALID_OTP);
        }

        String registerSessionToken = UUID.randomUUID().toString();
        // TTL of 30 minutes (1800 seconds) for the registration session
        try {
            redisCacheService.set("register_session:" + registerSessionToken, email, java.time.Duration.ofMinutes(30));
        } catch (Exception e) {
            log.warn("Failed to set register_session in Redis: {}", e.getMessage());
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
            HttpServletResponse httpResponse) {

        LoginUserCommand command = authPresentationMapper.toCommand(request);
        LoginUserResult result = loginUserCommandHandler.handle(command);
        LoginUserResponse response = authPresentationMapper.toResponse(result);

        if ("WEB".equalsIgnoreCase(clientType) && response.getRefreshToken() != null) {
            ResponseCookie springCookie = ResponseCookie.from("refreshToken", response.getRefreshToken())
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .maxAge(refreshTokenExpiration / 1000)
                    .sameSite("Strict")
                    .build();
            httpResponse.addHeader(HttpHeaders.SET_COOKIE, springCookie.toString());
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
            HttpServletResponse httpResponse) {

        String token = (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank())
                ? request.getRefreshToken()
                : cookieRefreshToken;

        RefreshTokenCommand command = RefreshTokenCommand.builder()
                .refreshToken(token)
                .deviceFingerprint(deviceFingerprint)
                .build();

        RefreshTokenResult result = refreshTokenCommandHandler.handle(command);
        RefreshTokenResponse response = authPresentationMapper.toResponse(result);

        if ("WEB".equalsIgnoreCase(clientType)) {
            ResponseCookie springCookie = ResponseCookie.from("refreshToken", response.getRefreshToken())
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .maxAge(refreshTokenExpiration / 1000)
                    .sameSite("Strict")
                    .build();
            httpResponse.addHeader(HttpHeaders.SET_COOKIE, springCookie.toString());
        }

        return ResponseEntity.ok(ApiResponse.success(response, MessageConstants.TOKEN_REFRESHED_SUCCESSFULLY));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user", description = "Revokes the current device token and logs the user out", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutUserRequest request) {
        LogoutUserCommand command = authPresentationMapper.toCommand(request);
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr != null) {
            command.setUserId(UUID.fromString(userIdStr));
            logoutUserCommandHandler.handle(command);
        }
        return ResponseEntity.ok(ApiResponse.success(null, MessageConstants.USER_LOGGED_OUT_SUCCESSFULLY));
    }
}
