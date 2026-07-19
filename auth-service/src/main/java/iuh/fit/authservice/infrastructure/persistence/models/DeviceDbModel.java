package iuh.fit.authservice.infrastructure.persistence.models;

import iuh.fit.authservice.domain.enums.DeviceStatus;
import iuh.fit.commonframework.domain.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "devices", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "device_fingerprint"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeviceDbModel extends BaseEntity {

    @NotNull(message = "{device.user.required}")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    UserDbModel user;

    @NotBlank(message = "{device.fingerprint.required}")
    @Size(max = 100, message = "{device.fingerprint.size}")
    @Column(name = "device_fingerprint", nullable = false, length = 100)
    String deviceFingerprint;

    @Size(max = 150, message = "{device.name.size}")
    @Column(name = "device_name", length = 150)
    String deviceName;

    @Size(max = 50, message = "{device.ipAddress.size}")
    @Column(name = "ip_address", length = 50)
    String ipAddress;

    @Size(max = 150, message = "{device.location.size}")
    @Column(length = 150)
    String location;

    @Column(name = "last_active_at")
    LocalDateTime lastActiveAt;

    @Column(name = "refresh_token_hash", length = 512)
    String refreshTokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    DeviceStatus status = DeviceStatus.ACTIVE;
}
