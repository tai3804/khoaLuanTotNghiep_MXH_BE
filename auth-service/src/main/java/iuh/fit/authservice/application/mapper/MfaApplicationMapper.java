package iuh.fit.authservice.application.mapper;

import iuh.fit.authservice.application.features.auth.commands.mfa_setup.MfaSetupResult;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface MfaApplicationMapper {

    MfaSetupResult toMfaSetupResult(String secretKey, String qrCodeUrl);
}
