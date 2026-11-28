package iuh.fit.authservice.infrastructure.persistence.jpa;

import iuh.fit.authservice.infrastructure.persistence.models.DeviceDbModel;
import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceJpaRepository extends BaseJpaRepository<DeviceDbModel, UUID> {
    List<DeviceDbModel> findByUserId(UUID userId);
    Optional<DeviceDbModel> findByUserIdAndDeviceFingerprint(UUID userId, String deviceFingerprint);
    Optional<DeviceDbModel> findByUserIdAndRefreshTokenHash(UUID userId, String refreshTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DeviceDbModel d where d.id = :id")
    Optional<DeviceDbModel> findByIdForUpdate(@Param("id") UUID id);
    void deleteAllByUserId(UUID userId);
}
