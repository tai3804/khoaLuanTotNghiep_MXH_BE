package iuh.fit.authservice.domain.repository;

import iuh.fit.authservice.domain.entities.Device;
import iuh.fit.commonframework.domain.repository.BaseRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends BaseRepository<Device, UUID> {
    List<Device> findByUserId(UUID userId);
    Optional<Device> findByUserIdAndDeviceFingerprint(UUID userId, String deviceFingerprint);
    Optional<Device> findByUserIdAndRefreshTokenHash(UUID userId, String refreshTokenHash);
    /** Đọc thiết bị và khóa dòng (SELECT ... FOR UPDATE) đến hết giao dịch: các lần làm mới token của cùng thiết bị chạy lần lượt. */
    Optional<Device> findByIdForUpdate(UUID id);
    void deleteAllByUserId(UUID userId);
}
