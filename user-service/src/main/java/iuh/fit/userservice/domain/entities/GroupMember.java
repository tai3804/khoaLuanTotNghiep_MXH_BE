package iuh.fit.userservice.domain.entities;

import iuh.fit.userservice.domain.enums.GroupMemberStatus;
import iuh.fit.userservice.domain.enums.GroupNotificationLevel;
import iuh.fit.userservice.domain.enums.GroupRole;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "group_members",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"group_id", "user_id"})},
    indexes = {
        // Danh sách thành viên theo trạng thái/vai trò của một nhóm, và các nhóm của một người
        @Index(name = "idx_group_member_group_status", columnList = "group_id, status"),
        @Index(name = "idx_group_member_user_status", columnList = "user_id, status")
    })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@EntityListeners({AuditingEntityListener.class, iuh.fit.userservice.infrastructure.persistence.SuggestionCacheEvictionListener.class})
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "group_id", nullable = false)
    UUID groupId;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    GroupRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    GroupMemberStatus status;

    @CreatedDate
    @Column(name = "joined_at", updatable = false)
    LocalDateTime joinedAt;

    /** Thời điểm yêu cầu tham gia được duyệt/từ chối (dùng để giới hạn xin lại sau khi bị từ chối). */
    @Column(name = "reviewed_at")
    LocalDateTime reviewedAt;

    /** Mức nhận thông báo của thành viên này; null được hiểu là HIGHLIGHTS. */
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_level", length = 20)
    GroupNotificationLevel notificationLevel;

    /** Người đã mời (khi status = INVITED hoặc thành viên vào nhóm nhờ lời mời). */
    @Column(name = "invited_by")
    UUID invitedBy;

    /** Câu trả lời cho câu hỏi tham gia nhóm riêng tư (mảng JSON theo thứ tự câu hỏi lúc gửi yêu cầu). */
    @Column(name = "join_answers", columnDefinition = "TEXT")
    String joinAnswers;
}
