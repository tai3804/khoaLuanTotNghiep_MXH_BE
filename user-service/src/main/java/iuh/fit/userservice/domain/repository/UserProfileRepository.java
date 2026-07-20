package iuh.fit.userservice.domain.repository;

import iuh.fit.userservice.infrastructure.persistence.models.UserProfileDbModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfileDbModel, UUID> {
    boolean existsByUserId(UUID userId);
}
