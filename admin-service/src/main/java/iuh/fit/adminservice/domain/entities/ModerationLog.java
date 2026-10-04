package iuh.fit.adminservice.domain.entities;

import iuh.fit.adminservice.domain.enums.ModerationAction;
import iuh.fit.adminservice.domain.enums.TargetType;
import iuh.fit.commonframework.domain.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "moderation_logs", indexes = {
        @Index(name = "idx_mod_log_moderator", columnList = "moderator_id"),
        @Index(name = "idx_mod_log_target", columnList = "target_type, target_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ModerationLog extends BaseEntity {

    @Column(name = "moderator_id")
    UUID moderatorId;

    @NotNull(message = "Target type is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    TargetType targetType;

    @NotNull(message = "Target ID is required")
    @Column(name = "target_id", nullable = false)
    UUID targetId;

    @NotNull(message = "Action is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    ModerationAction action;

    @Column(length = 100)
    String reason;

    @Size(max = 1000, message = "Note must not exceed 1000 characters")
    @Column(columnDefinition = "TEXT")
    String note;

    @Column(name = "report_id")
    UUID reportId;
}
