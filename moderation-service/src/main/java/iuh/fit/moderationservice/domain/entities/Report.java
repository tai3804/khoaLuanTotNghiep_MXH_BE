package iuh.fit.moderationservice.domain.entities;

import iuh.fit.commonframework.domain.entity.BaseEntity;
import iuh.fit.moderationservice.domain.enums.ReportReason;
import iuh.fit.moderationservice.domain.enums.ReportStatus;
import iuh.fit.moderationservice.domain.enums.TargetType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reports", indexes = {
        @Index(name = "idx_report_target", columnList = "target_type, target_id"),
        @Index(name = "idx_report_reporter", columnList = "reporter_id"),
        @Index(name = "idx_report_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Report extends BaseEntity {

    @NotNull(message = "{report.reporterId.required}")
    @Column(name = "reporter_id", nullable = false)
    UUID reporterId;

    @NotNull(message = "{report.targetType.required}")
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    TargetType targetType;

    @NotNull(message = "{report.targetId.required}")
    @Column(name = "target_id", nullable = false)
    UUID targetId;

    @NotNull(message = "{report.reason.required}")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    ReportReason reason;

    @Size(max = 1000, message = "{report.description.size}")
    @Column(columnDefinition = "TEXT")
    String description;

    @NotNull(message = "{report.status.required}")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    ReportStatus status = ReportStatus.PENDING;

    @Column(name = "resolved_at")
    LocalDateTime resolvedAt;

    @Column(name = "resolved_by")
    UUID resolvedBy;
}
