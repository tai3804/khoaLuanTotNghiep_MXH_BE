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
            HttpServletResponse httpResponse) {
        MfaVerifyResult result = mfaVerifyCommandHandler.handle(mfaPresentationMapper.toMfaVerifyCommand(request));
        LoginUserResponse response = mfaPresentationMapper.toLoginUserResponse(result);

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

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
