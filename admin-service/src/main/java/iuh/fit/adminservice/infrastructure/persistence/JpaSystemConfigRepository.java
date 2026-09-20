package iuh.fit.adminservice.infrastructure.persistence;

import iuh.fit.adminservice.domain.entities.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaSystemConfigRepository extends JpaRepository<SystemConfig, String> {
}
