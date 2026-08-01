package iuh.fit.userservice.domain.repository;

import iuh.fit.userservice.domain.entities.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
    boolean existsByUserId(UUID userId);
    Optional<UserProfile> findByUserId(UUID userId);
}
