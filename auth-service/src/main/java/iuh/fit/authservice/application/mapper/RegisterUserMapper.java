package iuh.fit.authservice.application.mapper;

import iuh.fit.authservice.application.features.auth.commands.register_user.RegisterUserCommand;
import iuh.fit.authservice.application.features.auth.commands.register_user.RegisterUserResult;
import iuh.fit.authservice.domain.entities.User;
import iuh.fit.authservice.domain.enums.UserStatus;
import iuh.fit.commonframework.application.mapper.BaseMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.Set;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, imports = {UserStatus.class, Set.class})
public interface RegisterUserMapper extends BaseMapper<User, RegisterUserResult> {

    @Mapping(target = "status", expression = "java(UserStatus.ACTIVE)")
    @Mapping(target = "roles", expression = "java(Set.of(\"ROLE_USER\"))")
    @Mapping(target = "permissions", expression = "java(Set.of())")
    @Mapping(target = "mfaEnabled", constant = "false")
    @Mapping(target = "password", ignore = true)
    User toEntityFromCommand(RegisterUserCommand command);
}
