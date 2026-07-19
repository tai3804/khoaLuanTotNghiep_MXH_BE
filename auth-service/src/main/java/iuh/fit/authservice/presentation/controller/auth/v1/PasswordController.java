package iuh.fit.authservice.presentation.controller.auth.v1;

import iuh.fit.authservice.application.features.auth.commands.change_password.ChangePasswordCommand;
import iuh.fit.authservice.application.features.auth.commands.change_password.ChangePasswordCommandHandler;
import iuh.fit.authservice.application.features.auth.commands.forgot_password.ForgotPasswordCommand;
import iuh.fit.authservice.application.features.auth.commands.forgot_password.ForgotPasswordCommandHandler;
import iuh.fit.authservice.application.features.auth.commands.reset_password.ResetPasswordCommand;
import iuh.fit.authservice.application.features.auth.commands.reset_password.ResetPasswordCommandHandler;
import iuh.fit.authservice.presentation.constants.ApiConstants;
import iuh.fit.authservice.presentation.dto.request.ChangePasswordRequest;
import iuh.fit.authservice.presentation.dto.request.ForgotPasswordRequest;
import iuh.fit.authservice.presentation.dto.request.ResetPasswordRequest;
import iuh.fit.authservice.presentation.mapper.PasswordPresentationMapper;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(ApiConstants.PASSWORD_API)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Password Management", description = "APIs for changing, forgetting, and resetting passwords")
public class PasswordController {

    ChangePasswordCommandHandler changePasswordCommandHandler;
    ForgotPasswordCommandHandler forgotPasswordCommandHandler;
    ResetPasswordCommandHandler resetPasswordCommandHandler;
    PasswordPresentationMapper passwordPresentationMapper;
    JwtUtil jwtUtil;

    @PostMapping("/change")
    @Operation(summary = "Change password", description = "Allows an authenticated user to change their password", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        ChangePasswordCommand command = passwordPresentationMapper.toCommand(request);
        
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr != null) {
            command.setUserId(UUID.fromString(userIdStr));
            changePasswordCommandHandler.handle(command);
        }

        return ResponseEntity.ok(ApiResponse.success(null, "Password changed successfully"));
    }

    @PostMapping("/forgot")
    @Operation(summary = "Forgot password", description = "Initiates the password reset process by generating a reset token")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        ForgotPasswordCommand command = passwordPresentationMapper.toCommand(request);
        forgotPasswordCommandHandler.handle(command);
        
        return ResponseEntity.ok(ApiResponse.success(null, "Password reset instructions have been sent (check logs)"));
    }

    @PostMapping("/reset")
    @Operation(summary = "Reset password", description = "Resets the user's password using a valid reset token")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        ResetPasswordCommand command = passwordPresentationMapper.toCommand(request);
        resetPasswordCommandHandler.handle(command);
        
        return ResponseEntity.ok(ApiResponse.success(null, "Password has been successfully reset"));
    }
}
