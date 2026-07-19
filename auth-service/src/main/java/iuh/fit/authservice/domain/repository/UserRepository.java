package iuh.fit.authservice.domain.repository;

import iuh.fit.authservice.domain.entities.User;

import java.util.Optional;

import java.util.UUID;

import iuh.fit.commonframework.domain.repository.BaseRepository;

public interface UserRepository extends BaseRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
