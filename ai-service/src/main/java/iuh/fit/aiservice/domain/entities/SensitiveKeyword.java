package iuh.fit.aiservice.domain.entities;

import iuh.fit.commonframework.domain.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "sensitive_keywords", indexes = {
        @Index(name = "idx_sensitive_keyword_word", columnList = "keyword"),
        @Index(name = "idx_sensitive_keyword_status", columnList = "status"),
        @Index(name = "idx_sensitive_keyword_auto", columnList = "is_auto_learned")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SensitiveKeyword extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    String keyword;

    @Column(nullable = false, length = 50)
    @Builder.Default
    String category = "PROFANITY"; // PROFANITY, HATE_SPEECH, HARASSMENT, SEXUAL, VIOLENCE, SPAM

    @Column(nullable = false, length = 20)
    @Builder.Default
    String severity = "MEDIUM"; // LOW, MEDIUM, HIGH, CRITICAL

    @Column(name = "hit_count", nullable = false)
    @Builder.Default
    int hitCount = 1;

    @Column(name = "is_auto_learned", nullable = false)
    @Builder.Default
    boolean isAutoLearned = true;

    @Column(nullable = false, length = 20)
    @Builder.Default
    String status = "ACTIVE"; // ACTIVE, PENDING_REVIEW, WHITELISTED

    @Column(name = "last_triggered_at")
    LocalDateTime lastTriggeredAt;

    @Column(name = "confidence_score")
    @Builder.Default
    double confidenceScore = 0.8;
}
