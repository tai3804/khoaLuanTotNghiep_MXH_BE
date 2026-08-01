package iuh.fit.authservice.presentation.mapper;

import iuh.fit.authservice.application.features.auth.commands.login_user.LoginUserCommand;
import iuh.fit.authservice.application.features.auth.commands.login_user.LoginUserResult;
import iuh.fit.authservice.application.features.auth.commands.logout_user.LogoutUserCommand;
import iuh.fit.authservice.application.features.auth.commands.refresh_token.RefreshTokenCommand;
import iuh.fit.authservice.application.features.auth.commands.refresh_token.RefreshTokenResult;
import iuh.fit.authservice.application.features.auth.commands.register_user.RegisterUserCommand;
import iuh.fit.authservice.application.features.auth.commands.register_user.RegisterUserResult;
import iuh.fit.authservice.presentation.dto.request.LoginUserRequest;
import iuh.fit.authservice.presentation.dto.request.LogoutUserRequest;
import iuh.fit.authservice.presentation.dto.request.RefreshTokenRequest;
import iuh.fit.authservice.presentation.dto.request.RegisterUserRequest;
import iuh.fit.authservice.presentation.dto.response.LoginUserResponse;
import iuh.fit.authservice.presentation.dto.response.RefreshTokenResponse;
import iuh.fit.authservice.presentation.dto.response.RegisterUserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AuthPresentationMapper {

    RegisterUserCommand toCommand(RegisterUserRequest request);

    LoginUserCommand toCommand(LoginUserRequest request);

    LogoutUserCommand toCommand(LogoutUserRequest request);

    RefreshTokenCommand toCommand(RefreshTokenRequest request);

    RegisterUserResponse toResponse(RegisterUserResult result);

    LoginUserResponse toResponse(LoginUserResult result);

    RefreshTokenResponse toResponse(RefreshTokenResult result);
}
