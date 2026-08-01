package iuh.fit.authservice.application.features.auth.commands.mfa_disable;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MfaDisableCommand {
    UUID userId;
    String otpCode;
}
