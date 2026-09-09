package iuh.fit.userservice.domain.entities;

import iuh.fit.commonframework.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "user_blocks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserBlock extends BaseEntity {

    @NotNull(message = "Blocker ID is required")
    @Column(name = "blocker_id", nullable = false)
    UUID blockerId;

    @NotNull(message = "Blocked ID is required")
    @Column(name = "blocked_id", nullable = false)
    UUID blockedId;

    @Column(name = "reason", length = 255)
    String reason;
}
