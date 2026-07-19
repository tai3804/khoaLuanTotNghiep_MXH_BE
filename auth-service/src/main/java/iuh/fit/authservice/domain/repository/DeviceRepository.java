package iuh.fit.authservice.domain.repository;

import iuh.fit.authservice.domain.entities.Device;

import java.util.List;
import java.util.Optional;

import java.util.UUID;

import iuh.fit.commonframework.domain.repository.BaseRepository;

public interface DeviceRepository extends BaseRepository<Device, UUID> {
    List<Device> findByUserId(UUID userId);
    Optional<Device> findByUserIdAndDeviceFingerprint(UUID userId, String deviceFingerprint);
}
