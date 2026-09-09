package iuh.fit.notificationservice.domain.repositories;

import iuh.fit.notificationservice.domain.entities.UserFcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserFcmTokenRepository extends JpaRepository<UserFcmToken, UUID> {

    Optional<UserFcmToken> findByUserIdAndFcmToken(UUID userId, String fcmToken);

    List<UserFcmToken> findByUserId(UUID userId);

    List<UserFcmToken> findByUserIdIn(List<UUID> userIds);

    void deleteByUserIdAndFcmToken(UUID userId, String fcmToken);

    void deleteByFcmToken(String fcmToken);
}
