package iuh.fit.authservice.infrastructure.persistence.mapper;

import iuh.fit.authservice.domain.entities.Device;
import iuh.fit.authservice.infrastructure.persistence.models.DeviceDbModel;
import iuh.fit.commonframework.application.mapper.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", uses = {UserModelMapper.class}, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DeviceModelMapper extends BaseMapper<DeviceDbModel, Device> {
}
