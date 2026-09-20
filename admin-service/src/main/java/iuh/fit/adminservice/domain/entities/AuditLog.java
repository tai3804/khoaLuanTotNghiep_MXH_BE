package iuh.fit.adminservice.domain.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String adminId;

    @Column(nullable = false)
    private String action; // e.g., "BAN_USER", "DELETE_POST", "ADD_BLACKLIST"

    @Column(nullable = false)
    private String targetId; // The ID of the affected resource

    private String details; // Extra JSON or string info

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
