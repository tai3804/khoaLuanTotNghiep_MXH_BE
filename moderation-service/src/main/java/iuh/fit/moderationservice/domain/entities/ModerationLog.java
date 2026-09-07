package iuh.fit.moderationservice.domain.entities;

import iuh.fit.commonframework.domain.entity.BaseEntity;
import iuh.fit.moderationservice.domain.enums.ModerationAction;
import iuh.fit.moderationservice.domain.enums.TargetType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "moderation_logs", indexes = {
        @Index(name = "idx_mod_log_target", columnList = "target_type, target_id"),
        @Index(name = "idx_mod_log_moderator", columnList = "moderator_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ModerationLog extends BaseEntity {

    @NotNull(message = "{moderationLog.moderatorId.required}")
    @Column(name = "moderator_id", nullable = false)
    UUID moderatorId;

    @NotNull(message = "{moderationLog.targetType.required}")
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    TargetType targetType;

    @NotNull(message = "{moderationLog.targetId.required}")
    @Column(name = "target_id", nullable = false)
    UUID targetId;

    @NotNull(message = "{moderationLog.action.required}")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    ModerationAction action;

    @Column(length = 500)
    String reason;

    @Column(columnDefinition = "TEXT")
    String note;

    @Column(name = "report_id")
    UUID reportId;
}
