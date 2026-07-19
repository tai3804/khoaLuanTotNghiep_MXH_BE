package iuh.fit.authservice.presentation.mapper;

import iuh.fit.authservice.application.features.auth.commands.change_password.ChangePasswordCommand;
import iuh.fit.authservice.application.features.auth.commands.forgot_password.ForgotPasswordCommand;
import iuh.fit.authservice.application.features.auth.commands.reset_password.ResetPasswordCommand;
import iuh.fit.authservice.presentation.dto.request.ChangePasswordRequest;
import iuh.fit.authservice.presentation.dto.request.ForgotPasswordRequest;
import iuh.fit.authservice.presentation.dto.request.ResetPasswordRequest;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PasswordPresentationMapper {

    ChangePasswordCommand toCommand(ChangePasswordRequest request);

    ForgotPasswordCommand toCommand(ForgotPasswordRequest request);

    ResetPasswordCommand toCommand(ResetPasswordRequest request);
}
