package iuh.fit.authservice.infrastructure.persistence.jpa;

import iuh.fit.authservice.infrastructure.persistence.models.UserDbModel;
import iuh.fit.commonframework.infrastructure.persistence.jpa.BaseJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserJpaRepository extends BaseJpaRepository<UserDbModel, UUID> {
    Optional<UserDbModel> findByEmail(String email);
    boolean existsByEmail(String email);
}
