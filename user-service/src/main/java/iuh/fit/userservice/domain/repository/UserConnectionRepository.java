package iuh.fit.userservice.domain.repository;

import iuh.fit.userservice.domain.entities.UserConnection;
import iuh.fit.userservice.domain.enums.ConnectionStatus;
import iuh.fit.userservice.domain.enums.ConnectionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserConnectionRepository extends JpaRepository<UserConnection, UUID> {
    Optional<UserConnection> findByRequesterIdAndTargetIdAndType(UUID requesterId, UUID targetId, ConnectionType type);
    boolean existsByRequesterIdAndTargetIdAndTypeAndStatus(UUID requesterId, UUID targetId, ConnectionType type, ConnectionStatus status);
}
