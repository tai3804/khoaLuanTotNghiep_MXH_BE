package iuh.fit.userservice.domain.entities;

import iuh.fit.commonframework.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/** Lịch sử thay đổi điểm uy tín: mỗi lần bị trừ (PENALTY) hoặc được hoàn lại (REFUND). */
@Entity
@Table(name = "reputation_events", indexes = {
        @Index(name = "idx_rep_event_user", columnList = "user_id"),
        @Index(name = "idx_rep_event_source", columnList = "user_id, source_type, source_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReputationEvent extends BaseEntity {

    public static final String PENALTY = "PENALTY";
    public static final String REFUND = "REFUND";

    @NotNull
    @Column(name = "user_id", nullable = false)
    UUID userId;

    /** PENALTY | REFUND */
    @Column(name = "type", nullable = false, length = 20)
    String type;

    /** WARN_USER | AUTO_HIDE | HIDE_POST | DELETE_POST (null với REFUND) */
    @Column(name = "action", length = 30)
    String action;

    @Column(name = "source_type", length = 20)
    String sourceType;

    @Column(name = "source_id")
    UUID sourceId;

    /** Số điểm thực sự bị trừ / hoàn lại (luôn dương). */
    @Column(name = "points", nullable = false)
    int points;

    @Column(name = "score_before", nullable = false)
    int scoreBefore;

    @Column(name = "score_after", nullable = false)
    int scoreAfter;

    @Column(name = "reason", length = 1000)
    String reason;

    @Column(name = "actor_id")
    UUID actorId;

    /** Khoản trừ đã được hoàn lại (quyết định kiểm duyệt bị thu hồi). */
    @Column(name = "reverted", nullable = false)
    @Builder.Default
    boolean reverted = false;
}
