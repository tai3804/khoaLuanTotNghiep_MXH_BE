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

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserConnectionRepository extends JpaRepository<UserConnection, UUID> {

    Optional<UserConnection> findByRequesterIdAndTargetIdAndType(UUID requesterId, UUID targetId, ConnectionType type);

    boolean existsByRequesterIdAndTargetIdAndTypeAndStatus(UUID requesterId, UUID targetId, ConnectionType type, ConnectionStatus status);

    @Query(UserConnectionQueryConstants.FIND_CONNECTION_BETWEEN)
    Optional<UserConnection> findConnectionBetween(@Param("user1") UUID user1, @Param("user2") UUID user2, @Param("type") ConnectionType type);

    @Query(UserConnectionQueryConstants.FIND_ACCEPTED_FRIENDSHIP)
    Optional<UserConnection> findAcceptedFriendship(@Param("user1") UUID user1, @Param("user2") UUID user2);

    @Query(UserConnectionQueryConstants.FIND_FRIENDS_OF_USER)
    Page<UserConnection> findFriendsOfUser(@Param("userId") UUID userId, Pageable pageable);

    @Query(UserConnectionQueryConstants.FIND_FOLLOWERS_OF_USER)
    Page<UserConnection> findFollowersOfUser(@Param("userId") UUID userId, Pageable pageable);

    @Query(UserConnectionQueryConstants.FIND_FOLLOWING_OF_USER)
    Page<UserConnection> findFollowingOfUser(@Param("userId") UUID userId, Pageable pageable);

    @Query(UserConnectionQueryConstants.FIND_PENDING_FRIEND_REQUESTS)
    Page<UserConnection> findPendingFriendRequestsForUser(@Param("userId") UUID userId, Pageable pageable);
}
