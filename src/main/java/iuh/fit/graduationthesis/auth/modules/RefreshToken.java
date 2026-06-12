package iuh.fit.graduationthesis.auth.modules;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(nullable = false, unique = true)
    String token;

    @Column(nullable = false)
    UUID accountId;

    @Column(nullable = false)
    Instant expiresAt;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    Instant createdAt = Instant.now();
}
