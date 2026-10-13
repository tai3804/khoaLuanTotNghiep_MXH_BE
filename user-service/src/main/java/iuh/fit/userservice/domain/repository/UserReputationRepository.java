package iuh.fit.userservice.domain.repository;

import iuh.fit.userservice.domain.entities.UserReputation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserReputationRepository extends JpaRepository<UserReputation, UUID> {
    Optional<UserReputation> findByUserId(UUID userId);
}
