package iuh.fit.authservice.presentation.dto.request;

import iuh.fit.authservice.domain.enums.MfaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MfaEnableRequest {
    @NotBlank(message = "{mfa.otpCode.required}")
    String otpCode;

    @NotNull(message = "{mfa.type.required}")
    MfaType mfaType;
}
