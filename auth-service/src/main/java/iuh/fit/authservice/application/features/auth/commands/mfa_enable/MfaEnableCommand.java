package iuh.fit.authservice.application.features.auth.commands.mfa_enable;

import iuh.fit.authservice.domain.enums.MfaType;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MfaEnableCommand {
    UUID userId;
    String otpCode;
    MfaType mfaType;
}
