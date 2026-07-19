package iuh.fit.authservice.infrastructure.persistence.models;

import iuh.fit.authservice.domain.enums.UserStatus;
import iuh.fit.commonframework.domain.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserDbModel extends BaseEntity {

    @NotBlank(message = "{user.email.required}")
    @Email(message = "{user.email.invalid}")
    @Size(max = 100, message = "{user.email.size}")
    @Column(nullable = false, unique = true, length = 100)
    String email;

    @NotBlank(message = "{user.password.required}")
    @Column(nullable = false)
    String password;

    @Size(max = 100, message = "{user.fullName.size}")
    @Column(name = "full_name", length = 100)
    String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    UserStatus status = UserStatus.ACTIVE;

    @Column(name = "mfa_enabled", nullable = false)
    @Builder.Default
    boolean mfaEnabled = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    @Builder.Default
    Set<String> roles = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_permissions", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "permission")
    @Builder.Default
    Set<String> permissions = new HashSet<>();
}
