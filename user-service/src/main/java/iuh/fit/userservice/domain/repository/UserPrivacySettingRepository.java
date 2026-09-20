package iuh.fit.userservice.domain.repository;

import iuh.fit.userservice.domain.entities.UserPrivacySetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPrivacySettingRepository extends JpaRepository<UserPrivacySetting, UUID> {
    Optional<UserPrivacySetting> findByUserId(UUID userId);
}
