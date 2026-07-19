package iuh.fit.authservice.presentation.controller.auth.v1;

import iuh.fit.authservice.application.features.auth.commands.login_user.LoginUserCommand;
import iuh.fit.authservice.application.features.auth.commands.login_user.LoginUserCommandHandler;
import iuh.fit.authservice.application.features.auth.commands.login_user.LoginUserResult;
import iuh.fit.authservice.application.features.auth.commands.logout_user.LogoutUserCommand;
import iuh.fit.authservice.application.features.auth.commands.logout_user.LogoutUserCommandHandler;
import iuh.fit.authservice.application.features.auth.commands.register_user.RegisterUserCommand;
import iuh.fit.authservice.application.features.auth.commands.register_user.RegisterUserCommandHandler;
import iuh.fit.authservice.application.features.auth.commands.register_user.RegisterUserResult;
import iuh.fit.authservice.presentation.dto.request.LoginUserRequest;
import iuh.fit.authservice.presentation.dto.request.LogoutUserRequest;
import iuh.fit.authservice.presentation.dto.request.RegisterUserRequest;
import iuh.fit.authservice.presentation.dto.response.LoginUserResponse;
import iuh.fit.authservice.presentation.dto.response.RegisterUserResponse;
import iuh.fit.authservice.presentation.mapper.AuthPresentationMapper;
import iuh.fit.commonframework.application.dto.ApiResponse;
import iuh.fit.commonframework.infrastructure.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import iuh.fit.authservice.presentation.constants.ApiConstants;

@RestController
@RequestMapping(ApiConstants.AUTH_API)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Authentication", description = "APIs for user login, register, and logout")
public class AuthController {

    RegisterUserCommandHandler registerUserCommandHandler;
    LoginUserCommandHandler loginUserCommandHandler;
    LogoutUserCommandHandler logoutUserCommandHandler;
    AuthPresentationMapper authPresentationMapper;
    JwtUtil jwtUtil;

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates a new user account with email and password")
    public ResponseEntity<ApiResponse<RegisterUserResponse>> register(@Valid @RequestBody RegisterUserRequest request) {
        RegisterUserCommand command = authPresentationMapper.toCommand(request);
        RegisterUserResult result = registerUserCommandHandler.handle(command);
        RegisterUserResponse response = authPresentationMapper.toResponse(result);
        return ResponseEntity.ok(ApiResponse.success(response, "User registered successfully"));
    }

    @PostMapping("/login")
    @Operation(summary = "Login user", description = "Authenticates a user and returns a JWT access token")
    public ResponseEntity<ApiResponse<LoginUserResponse>> login(@Valid @RequestBody LoginUserRequest request) {
        LoginUserCommand command = authPresentationMapper.toCommand(request);
        LoginUserResult result = loginUserCommandHandler.handle(command);
        LoginUserResponse response = authPresentationMapper.toResponse(result);
        return ResponseEntity.ok(ApiResponse.success(response, "User logged in successfully"));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user", description = "Revokes the current device token and logs the user out", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutUserRequest request) {
        LogoutUserCommand command = authPresentationMapper.toCommand(request);
        // Securely bind the logout action to the currently authenticated user
        String userIdStr = jwtUtil.getCurrentUserId();
        if (userIdStr != null) {
            command.setUserId(UUID.fromString(userIdStr));
            logoutUserCommandHandler.handle(command);
        }
        return ResponseEntity.ok(ApiResponse.success(null, "User logged out successfully"));
    }
}
