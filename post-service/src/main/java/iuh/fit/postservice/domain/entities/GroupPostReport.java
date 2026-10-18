package iuh.fit.postservice.domain.entities;

import iuh.fit.commonframework.domain.entity.BaseEntity;
import iuh.fit.postservice.domain.enums.GroupReportStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/** Thành viên báo cáo một bài viết trong nhóm cho quản trị viên nhóm (khác báo cáo gửi quản trị hệ thống). */
@Entity
@Table(name = "group_post_reports",
        indexes = {
                @Index(name = "idx_group_report_group_status", columnList = "group_id, status"),
                @Index(name = "idx_group_report_post", columnList = "post_id")
        },
        uniqueConstraints = @UniqueConstraint(name = "uk_group_report_post_reporter", columnNames = {"post_id", "reporter_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GroupPostReport extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    UUID postId;

    @Column(name = "group_id", nullable = false)
    UUID groupId;

    @Column(name = "reporter_id", nullable = false)
    UUID reporterId;

    @Column(name = "reason", nullable = false, length = 100)
    String reason;

    @Column(name = "detail", length = 1000)
    String detail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    GroupReportStatus status = GroupReportStatus.OPEN;

    @Column(name = "handled_by")
    UUID handledBy;

    @Column(name = "handled_at")
    LocalDateTime handledAt;
}
