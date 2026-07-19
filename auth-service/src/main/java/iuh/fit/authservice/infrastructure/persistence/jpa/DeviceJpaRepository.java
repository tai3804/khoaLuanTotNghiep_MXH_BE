package iuh.fit.authservice.infrastructure.persistence.jpa;

import iuh.fit.authservice.infrastructure.persistence.models.DeviceDbModel;
import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceJpaRepository extends BaseJpaRepository<DeviceDbModel, UUID> {
    List<DeviceDbModel> findByUserId(UUID userId);
    Optional<DeviceDbModel> findByUserIdAndDeviceFingerprint(UUID userId, String deviceFingerprint);
}
