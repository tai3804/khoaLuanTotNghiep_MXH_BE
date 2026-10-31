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
@jakarta.persistence.EntityListeners(iuh.fit.userservice.infrastructure.persistence.SuggestionCacheEvictionListener.class)
@Table(name = "user_connections", indexes = {
        @Index(name = "idx_conn_req_target_type", columnList = "requester_id, target_id, type"),
        @Index(name = "idx_conn_target_type_status", columnList = "target_id, type, status"),
        @Index(name = "idx_conn_req_type_status", columnList = "requester_id, type, status")
})
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
