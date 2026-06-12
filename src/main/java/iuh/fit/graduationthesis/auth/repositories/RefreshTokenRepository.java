package iuh.fit.graduationthesis.auth.repositories;

import iuh.fit.graduationthesis.auth.modules.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByToken(String token);

    void deleteByAccountId(UUID accountId);
}
