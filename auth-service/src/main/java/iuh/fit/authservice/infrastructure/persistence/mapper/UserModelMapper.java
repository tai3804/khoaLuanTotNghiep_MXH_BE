package iuh.fit.authservice.infrastructure.persistence.mapper;

import iuh.fit.authservice.domain.entities.User;
import iuh.fit.authservice.infrastructure.persistence.models.UserDbModel;
import iuh.fit.commonframework.application.mapper.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserModelMapper extends BaseMapper<UserDbModel, User> {
}
