package iuh.fit.userservice.domain.entities;

import iuh.fit.commonframework.domain.entity.BaseEntity;
import iuh.fit.userservice.domain.enums.GroupPrivacy;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Entity
@Table(name = "groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@SQLRestriction("deleted = false")
public class Group extends BaseEntity {


    @Column(nullable = false, length = 100)
    String name;

    @Column(length = 1000)
    String description;

    @Column(name = "cover_url", length = 500)
    String coverUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    GroupPrivacy privacy;

    @Column(name = "creator_id", nullable = false)
    UUID creatorId;

    @Builder.Default
    @Column(name = "member_count", nullable = false)
    long memberCount = 1;
}

