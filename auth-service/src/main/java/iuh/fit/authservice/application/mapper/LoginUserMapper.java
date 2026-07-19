package iuh.fit.authservice.application.mapper;

import iuh.fit.authservice.application.features.auth.commands.login_user.LoginUserCommand;
import iuh.fit.authservice.application.features.auth.commands.login_user.LoginUserResult;
import iuh.fit.authservice.application.features.auth.commands.login_user.UserResult;
import iuh.fit.authservice.domain.entities.Device;
import iuh.fit.authservice.domain.entities.User;
import iuh.fit.authservice.domain.enums.DeviceStatus;
import iuh.fit.commonframework.application.mapper.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

import java.time.LocalDateTime;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, imports = {DeviceStatus.class, LocalDateTime.class})
public interface LoginUserMapper extends BaseMapper<User, UserResult> {

    LoginUserResult toLoginUserResult(String accessToken, String refreshToken, UserResult user);

    @Mapping(target = "lastActiveAt", expression = "java(LocalDateTime.now())")
    @Mapping(target = "status", expression = "java(DeviceStatus.ACTIVE)")
    @Mapping(target = "refreshTokenHash", source = "hashedToken")
    void updateDevice(LoginUserCommand command, String hashedToken, @MappingTarget Device device);
}
