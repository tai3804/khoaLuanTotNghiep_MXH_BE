package iuh.fit.userservice.domain.entities;

import iuh.fit.commonframework.domain.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "user_privacy_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserPrivacySetting extends BaseEntity {

    @NotNull
    @Column(name = "user_id", nullable = false, unique = true)
    UUID userId;

    @Column(name = "default_post_privacy", length = 30)
    @Builder.Default
    String defaultPostPrivacy = "PUBLIC";

    @Column(name = "friend_request_privacy", length = 30)
    @Builder.Default
    String friendRequestPrivacy = "EVERYONE";

    @Column(name = "friend_list_privacy", length = 30)
    @Builder.Default
    String friendListPrivacy = "PUBLIC";

    @Column(name = "search_privacy", length = 30)
    @Builder.Default
    String searchPrivacy = "EVERYONE";
}
