package iuh.fit.userservice.infrastructure.persistence.models;

import iuh.fit.commonframework.domain.entity.BaseEntity;
import iuh.fit.userservice.domain.enums.ConnectionStatus;
import iuh.fit.userservice.domain.enums.ConnectionType;
import jakarta.persistence.*;
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
public class UserConnectionDbModel extends BaseEntity {

    @Column(name = "requester_id", nullable = false)
    UUID requesterId;

    @Column(name = "target_id", nullable = false)
    UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    ConnectionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    ConnectionStatus status = ConnectionStatus.PENDING;
}
