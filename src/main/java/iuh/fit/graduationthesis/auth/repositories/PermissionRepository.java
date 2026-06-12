package iuh.fit.graduationthesis.auth.repositories;

import iuh.fit.graduationthesis.auth.modules.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByName(String name);
}
