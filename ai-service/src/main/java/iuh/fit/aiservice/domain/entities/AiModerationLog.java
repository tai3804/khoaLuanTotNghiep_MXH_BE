package iuh.fit.aiservice.domain.entities;

import iuh.fit.commonframework.domain.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "ai_moderation_logs", indexes = {
        @Index(name = "idx_ai_log_target", columnList = "target_id"),
        @Index(name = "idx_ai_log_action", columnList = "action_taken"),
        @Index(name = "idx_ai_log_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AiModerationLog extends BaseEntity {

    @Column(name = "target_type", nullable = false, length = 20)
    @Builder.Default
    String targetType = "POST";

    @Column(name = "target_id", nullable = false)
    UUID targetId;

    @Column(name = "author_id")
    UUID authorId;

    @Column(name = "report_id")
    UUID reportId;

    @Column(name = "content_snippet", columnDefinition = "TEXT")
    String contentSnippet;

    @Column(name = "toxicity_score", nullable = false)
    double toxicityScore;

    @Column(length = 50)
    String category;

    @Column(length = 20)
    String severity;

    @Column(name = "action_taken", nullable = false, length = 30)
    String actionTaken; // ALLOW, WARN_USER, AUTO_HIDE, DELETE_POST

    @Column(columnDefinition = "TEXT")
    String reason;

    @Column(name = "extracted_keywords")
    String extractedKeywords;

    @Column(name = "is_fallback", nullable = false)
    @Builder.Default
    boolean isFallback = false;
}
