package iuh.fit.userservice.domain.entities;

import iuh.fit.commonframework.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Điểm uy tín hiện tại của người dùng. Chưa có bản ghi nghĩa là chưa vi phạm lần nào (điểm tối đa). */
@Entity
@Table(name = "user_reputation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserReputation extends BaseEntity {

    @NotNull
    @Column(name = "user_id", nullable = false, unique = true)
    UUID userId;

    @Column(name = "score", nullable = false)
    @Builder.Default
    int score = 100;
}
