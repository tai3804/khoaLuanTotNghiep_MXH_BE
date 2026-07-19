package iuh.fit.authservice.infrastructure.persistence.repository_impl;

import iuh.fit.authservice.domain.entities.User;
import iuh.fit.authservice.domain.repository.UserRepository;
import iuh.fit.authservice.infrastructure.persistence.jpa.UserJpaRepository;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import iuh.fit.authservice.infrastructure.persistence.mapper.UserModelMapper;
import iuh.fit.authservice.infrastructure.persistence.models.UserDbModel;

import java.util.Optional;

import iuh.fit.commonframework.infrastructure.persistence.repository_impl.BaseRepositoryImpl;

@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserRepositoryImpl extends BaseRepositoryImpl<User, java.util.UUID, UserDbModel> implements UserRepository {

    UserJpaRepository userJpaRepository;
    UserModelMapper userModelMapper;

    public UserRepositoryImpl(UserJpaRepository userJpaRepository, UserModelMapper userModelMapper) {
        super(userJpaRepository, userModelMapper);
        this.userJpaRepository = userJpaRepository;
        this.userModelMapper = userModelMapper;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userJpaRepository.findByEmail(email).map(userModelMapper::toDto);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userJpaRepository.existsByEmail(email);
    }


}
