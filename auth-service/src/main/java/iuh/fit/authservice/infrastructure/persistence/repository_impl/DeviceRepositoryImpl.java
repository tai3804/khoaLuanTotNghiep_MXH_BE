package iuh.fit.authservice.infrastructure.persistence.repository_impl;

import iuh.fit.authservice.domain.entities.Device;
import iuh.fit.authservice.domain.repository.DeviceRepository;
import iuh.fit.authservice.infrastructure.persistence.jpa.DeviceJpaRepository;
import iuh.fit.authservice.infrastructure.persistence.mapper.DeviceModelMapper;
import iuh.fit.authservice.infrastructure.persistence.models.DeviceDbModel;
import iuh.fit.commonframework.infrastructure.persistence.repository_impl.BaseRepositoryImpl;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DeviceRepositoryImpl extends BaseRepositoryImpl<Device, UUID, DeviceDbModel> implements DeviceRepository {

    DeviceJpaRepository deviceJpaRepository;
    DeviceModelMapper deviceModelMapper;

    public DeviceRepositoryImpl(DeviceJpaRepository deviceJpaRepository, DeviceModelMapper deviceModelMapper) {
        super(deviceJpaRepository, deviceModelMapper);
        this.deviceJpaRepository = deviceJpaRepository;
        this.deviceModelMapper = deviceModelMapper;
    }

    @Override
    public List<Device> findByUserId(UUID userId) {
        return deviceJpaRepository.findByUserId(userId).stream()
                .map(deviceModelMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Device> findByUserIdAndDeviceFingerprint(UUID userId, String deviceFingerprint) {
        return deviceJpaRepository.findByUserIdAndDeviceFingerprint(userId, deviceFingerprint)
                .map(deviceModelMapper::toDto);
    }

    @Override
    public void deleteAllByUserId(UUID userId) {
        deviceJpaRepository.deleteAllByUserId(userId);
    }
}
