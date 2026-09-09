package iuh.fit.userservice.domain.repository;

import iuh.fit.userservice.domain.entities.UserBlock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface UserBlockRepository extends JpaRepository<UserBlock, UUID> {

    Optional<UserBlock> findByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    Page<UserBlock> findByBlockerId(UUID blockerId, Pageable pageable);

    void deleteByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM UserBlock b WHERE " +
           "(b.blockerId = :user1 AND b.blockedId = :user2) OR (b.blockerId = :user2 AND b.blockedId = :user1)")
    boolean isBlockedBetween(@Param("user1") UUID user1, @Param("user2") UUID user2);

    @Query("SELECT CASE WHEN b.blockerId = :userId THEN b.blockedId ELSE b.blockerId END FROM UserBlock b WHERE b.blockerId = :userId OR b.blockedId = :userId")
    Set<UUID> findAllBlockedUserIdsRelatedTo(@Param("userId") UUID userId);
}
