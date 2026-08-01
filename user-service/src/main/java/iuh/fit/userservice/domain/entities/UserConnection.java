package iuh.fit.userservice.domain.entities;

import iuh.fit.commonframework.domain.entity.BaseEntity;
import iuh.fit.userservice.domain.enums.ConnectionStatus;
import iuh.fit.userservice.domain.enums.ConnectionType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "user_connections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserConnection extends BaseEntity {

    @NotNull(message = "{userConnection.requesterId.required}")
    @Column(name = "requester_id", nullable = false)
    UUID requesterId;

    @NotNull(message = "{userConnection.targetId.required}")
    @Column(name = "target_id", nullable = false)
    UUID targetId;

    @NotNull(message = "{userConnection.type.required}")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    ConnectionType type;

    @NotNull(message = "{userConnection.status.required}")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    ConnectionStatus status = ConnectionStatus.PENDING;
}
