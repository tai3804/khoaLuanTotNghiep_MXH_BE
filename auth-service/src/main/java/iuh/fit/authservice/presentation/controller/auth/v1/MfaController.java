package iuh.fit.authservice.presentation.controller.auth.v1;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import iuh.fit.authservice.application.exception.AuthErrorCode;
import iuh.fit.authservice.application.features.auth.commands.mfa_disable.MfaDisableCommandHandler;
import iuh.fit.authservice.application.features.auth.commands.mfa_enable.MfaEnableCommandHandler;
import iuh.fit.authservice.application.features.auth.commands.mfa_setup.MfaSetupCommand;
import iuh.fit.authservice.application.features.auth.commands.mfa_setup.MfaSetupCommandHandler;
import iuh.fit.authservice.application.features.auth.commands.mfa_setup.MfaSetupResult;
import iuh.fit.authservice.application.features.auth.commands.mfa_verify.MfaVerifyCommandHandler;
import iuh.fit.authservice.application.features.auth.commands.mfa_verify.MfaVerifyResult;
import iuh.fit.authservice.presentation.constants.ApiConstants;
import iuh.fit.authservice.presentation.dto.request.MfaDisableRequest;
import iuh.fit.authservice.presentation.dto.request.MfaEnableRequest;
import iuh.fit.authservice.presentation.dto.request.MfaVerifyRequest;
import iuh.fit.authservice.presentation.dto.response.LoginUserResponse;
import iuh.fit.authservice.presentation.dto.response.MfaSetupResponse;
import iuh.fit.authservice.presentation.mapper.MfaPresentationMapper;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.application.exception.BusinessException;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.AUTH_API + "/mfa")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MfaController {

    MfaSetupCommandHandler mfaSetupCommandHandler;
    MfaEnableCommandHandler mfaEnableCommandHandler;
    MfaDisableCommandHandler mfaDisableCommandHandler;
    MfaVerifyCommandHandler mfaVerifyCommandHandler;
    MfaPresentationMapper mfaPresentationMapper;
    JwtUtil jwtUtil;

    @NonFinal
    @Value("${app.security.jwt.expiration.refresh-token}")
    long refreshTokenExpiration;

    @PostMapping("/setup")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<MfaSetupResponse>> setupMfa() {
        String currentUserIdStr = jwtUtil.getCurrentUserId();
        if (currentUserIdStr == null) {
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }

        UUID userId = UUID.fromString(currentUserIdStr);
        MfaSetupResult result = mfaSetupCommandHandler.handle(MfaSetupCommand.builder().userId(userId).build());
        return ResponseEntity.ok(ApiResponse.success(mfaPresentationMapper.toMfaSetupResponse(result)));
    }

    @PostMapping("/enable")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<Void>> enableMfa(@Valid @RequestBody MfaEnableRequest request) {
        String currentUserIdStr = jwtUtil.getCurrentUserId();
        if (currentUserIdStr == null) {
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }

        UUID userId = UUID.fromString(currentUserIdStr);
        mfaEnableCommandHandler.handle(mfaPresentationMapper.toMfaEnableCommand(userId, request));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/disable")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<Void>> disableMfa(@Valid @RequestBody MfaDisableRequest request) {
        String currentUserIdStr = jwtUtil.getCurrentUserId();
        if (currentUserIdStr == null) {
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }

        UUID userId = UUID.fromString(currentUserIdStr);
        mfaDisableCommandHandler.handle(mfaPresentationMapper.toMfaDisableCommand(userId, request));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<LoginUserResponse>> verifyMfa(
            @Valid @RequestBody MfaVerifyRequest request,
            @RequestHeader(value = "X-Client-Type", defaultValue = "WEB") String clientType,
            @RequestHeader(value = "X-Device-Fingerprint", required = false) String headerFingerprint,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        var command = mfaPresentationMapper.toMfaVerifyCommand(request);
        if (command.getDeviceFingerprint() == null || command.getDeviceFingerprint().isBlank()) {
            command.setDeviceFingerprint(headerFingerprint);
        }
        if (command.getDeviceName() == null || command.getDeviceName().isBlank()) {
            command.setDeviceName(httpRequest.getHeader("User-Agent"));
        }
        command.setIpAddress(extractClientIp(httpRequest));

        MfaVerifyResult result = mfaVerifyCommandHandler.handle(command);
        LoginUserResponse response = mfaPresentationMapper.toLoginUserResponse(result);

        if (response.getRefreshToken() != null) {
            setRefreshTokenCookie(httpRequest, httpResponse, response.getRefreshToken(), refreshTokenExpiration / 1000);
        }

        return ResponseEntity.ok(ApiResponse.success(response));
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

    private String extractClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
