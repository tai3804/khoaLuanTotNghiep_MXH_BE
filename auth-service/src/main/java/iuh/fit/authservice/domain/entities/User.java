package iuh.fit.authservice.domain.entities;

import iuh.fit.authservice.domain.enums.MfaType;
import iuh.fit.authservice.domain.enums.UserStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User {
    UUID id;
    String email;
    String password;
    String firstName;
    String lastName;
    String middleName;
    UserStatus status;
    Set<String> roles;
    Set<String> permissions;
    boolean mfaEnabled;
    String mfaSecret;
    @Builder.Default
    MfaType mfaType = MfaType.NONE;
    @Builder.Default
    Integer tokenVersion = 1;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    boolean deleted;
}
