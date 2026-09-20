package iuh.fit.userservice.presentation.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PrivacySettingsDto {
    String defaultPostPrivacy;
    String friendRequestPrivacy;
    String friendListPrivacy;
    String searchPrivacy;
}
