package iuh.fit.authservice.presentation.mapper;

import iuh.fit.authservice.application.features.auth.commands.mfa_disable.MfaDisableCommand;
import iuh.fit.authservice.application.features.auth.commands.mfa_enable.MfaEnableCommand;
import iuh.fit.authservice.application.features.auth.commands.mfa_setup.MfaSetupResult;
import iuh.fit.authservice.application.features.auth.commands.mfa_verify.MfaVerifyCommand;
import iuh.fit.authservice.application.features.auth.commands.mfa_verify.MfaVerifyResult;
import iuh.fit.authservice.presentation.dto.request.MfaDisableRequest;
import iuh.fit.authservice.presentation.dto.request.MfaEnableRequest;
import iuh.fit.authservice.presentation.dto.request.MfaVerifyRequest;
import iuh.fit.authservice.presentation.dto.response.LoginUserResponse;
import iuh.fit.authservice.presentation.dto.response.MfaSetupResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MfaPresentationMapper {

    MfaSetupResponse toMfaSetupResponse(MfaSetupResult result);

    MfaEnableCommand toMfaEnableCommand(UUID userId, MfaEnableRequest request);

    MfaDisableCommand toMfaDisableCommand(UUID userId, MfaDisableRequest request);

    MfaVerifyCommand toMfaVerifyCommand(MfaVerifyRequest request);

    LoginUserResponse toLoginUserResponse(MfaVerifyResult result);
}
