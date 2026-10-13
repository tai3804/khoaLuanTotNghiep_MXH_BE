package iuh.fit.adminservice.domain.entities;

import iuh.fit.adminservice.domain.enums.ReportReason;
import iuh.fit.adminservice.domain.enums.ReportSource;
import iuh.fit.adminservice.domain.enums.ReportStatus;
import iuh.fit.adminservice.domain.enums.TargetType;
import iuh.fit.commonframework.domain.entity.BaseEntity;
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

    @NotNull(message = "Reporter ID is required")
    @Column(name = "reporter_id", nullable = false)
    UUID reporterId;

    @NotNull(message = "Target type is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    TargetType targetType;

    @NotNull(message = "Target ID is required")
    @Column(name = "target_id", nullable = false)
    UUID targetId;

    @NotNull(message = "Report reason is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    ReportReason reason;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Column(columnDefinition = "TEXT")
    String description;

    @NotNull(message = "Report status is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    ReportStatus status = ReportStatus.PENDING;

    @Column(name = "resolved_at")
    LocalDateTime resolvedAt;

    @Column(name = "resolved_by")
    UUID resolvedBy;

    @Column(name = "appeal_message", columnDefinition = "TEXT")
    String appealMessage;

    @Column(name = "appealed_at")
    LocalDateTime appealedAt;

    // Báo cáo do AI tự động gắn cờ. Các cột nullable để dữ liệu cũ (báo cáo của người dùng) vẫn hợp lệ: source null = USER.
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    ReportSource source;

    @Column(name = "ai_score")
    Double aiScore;

    @Column(name = "ai_category", length = 50)
    String aiCategory;

    @Column(name = "ai_severity", length = 20)
    String aiSeverity;

    @Column(name = "ai_reason", columnDefinition = "TEXT")
    String aiReason;
}
