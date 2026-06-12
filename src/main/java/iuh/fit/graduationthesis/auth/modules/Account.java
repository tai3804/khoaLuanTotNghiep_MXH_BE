package iuh.fit.graduationthesis.auth.modules;

import iuh.fit.graduationthesis.auth.modules.enums.Role;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import iuh.fit.graduationthesis.auth.modules.enums.ValidationMessage.Constants;

import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @NotBlank(message = Constants.USERNAME_NOT_EMPTY)
    @Column(unique = true, nullable = false, length = 50)
    String userName;

    @NotBlank(message = Constants.PASSWORD_NOT_EMPTY)
    @Column(nullable = false)
    String password;

    @NotEmpty(message = Constants.ROLE_NOT_EMPTY)
    @ElementCollection(targetClass = Role.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "account_roles", joinColumns = @JoinColumn(name = "account_id"))
    @Enumerated(EnumType.STRING)
    Set<Role> roles;

    @NotEmpty(message = Constants.PERMISSION_NOT_EMPTY)
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "account_permissions",
            joinColumns = @JoinColumn(name = "account_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    Set<Permission> permissions;

    @NotNull(message = Constants.USER_ID_NOT_NULL)
    @Column(nullable = false)
    UUID userId;
}