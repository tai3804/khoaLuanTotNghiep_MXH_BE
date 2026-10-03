package iuh.fit.userservice.domain.repository;

import iuh.fit.userservice.domain.entities.UserConnection;
import iuh.fit.userservice.domain.enums.ConnectionStatus;
import iuh.fit.userservice.domain.enums.ConnectionType;
import iuh.fit.userservice.infrastructure.persistence.constants.UserConnectionQueryConstants;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserConnectionRepository extends JpaRepository<UserConnection, UUID> {

    Optional<UserConnection> findFirstByRequesterIdAndTargetIdAndTypeOrderByCreatedAtDesc(UUID requesterId, UUID targetId, ConnectionType type);

    List<UserConnection> findAllByRequesterIdAndTargetIdAndTypeOrderByCreatedAtDesc(UUID requesterId, UUID targetId, ConnectionType type);

    boolean existsByRequesterIdAndTargetIdAndTypeAndStatus(UUID requesterId, UUID targetId, ConnectionType type, ConnectionStatus status);

    @Query(UserConnectionQueryConstants.FIND_CONNECTION_BETWEEN)
    List<UserConnection> findAllConnectionBetween(@Param("user1") UUID user1, @Param("user2") UUID user2, @Param("type") ConnectionType type);

    default Optional<UserConnection> findConnectionBetween(UUID user1, UUID user2, ConnectionType type) {
        List<UserConnection> list = findAllConnectionBetween(user1, user2, type);
        if (list.isEmpty()) {
            return Optional.empty();
        }
        if (list.size() > 1) {
            UserConnection chosen = list.stream()
                    .filter(c -> c.getStatus() == ConnectionStatus.ACCEPTED)
                    .findFirst()
                    .orElse(list.get(0));
            for (UserConnection c : list) {
                if (!c.getId().equals(chosen.getId())) {
                    try {
                        delete(c);
                    } catch (Exception ignored) {}
                }
            }
            return Optional.of(chosen);
        }
        return Optional.of(list.get(0));
    }

    @Query("SELECT c FROM UserConnection c WHERE " +
           "((c.requesterId = :user1 AND c.targetId = :user2) OR (c.requesterId = :user2 AND c.targetId = :user1))")
    List<UserConnection> findAllConnectionsBetween(@Param("user1") UUID user1, @Param("user2") UUID user2);

    @Query(UserConnectionQueryConstants.FIND_ACCEPTED_FRIENDSHIP)
    List<UserConnection> findAllAcceptedFriendshipsBetween(@Param("user1") UUID user1, @Param("user2") UUID user2);

    default Optional<UserConnection> findAcceptedFriendship(UUID user1, UUID user2) {
        List<UserConnection> list = findAllAcceptedFriendshipsBetween(user1, user2);
        if (list.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(list.get(0));
    }

    @Query(UserConnectionQueryConstants.FIND_FRIENDS_OF_USER)
    Page<UserConnection> findFriendsOfUser(@Param("userId") UUID userId, Pageable pageable);

    @Query(UserConnectionQueryConstants.FIND_FOLLOWERS_OF_USER)
    Page<UserConnection> findFollowersOfUser(@Param("userId") UUID userId, Pageable pageable);

    @Query(UserConnectionQueryConstants.FIND_FOLLOWING_OF_USER)
    Page<UserConnection> findFollowingOfUser(@Param("userId") UUID userId, Pageable pageable);

    @Query(UserConnectionQueryConstants.FIND_PENDING_FRIEND_REQUESTS)
    Page<UserConnection> findPendingFriendRequestsForUser(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT c FROM UserConnection c WHERE (c.requesterId = :userId OR c.targetId = :userId) AND c.type = 'FRIEND' AND c.status = 'ACCEPTED'")
    List<UserConnection> findAllAcceptedFriendships(@Param("userId") UUID userId);

    @Query("SELECT c FROM UserConnection c WHERE (c.requesterId IN :userIds OR c.targetId IN :userIds) AND c.type = 'FRIEND' AND c.status = 'ACCEPTED'")
    List<UserConnection> findAllAcceptedFriendshipsForUserIds(@Param("userIds") java.util.Collection<UUID> userIds);
}
