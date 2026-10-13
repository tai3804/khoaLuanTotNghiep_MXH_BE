package iuh.fit.userservice.domain.repository;

import iuh.fit.userservice.domain.entities.UserProfile;
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
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
    boolean existsByUserId(UUID userId);

    Optional<UserProfile> findByUserId(UUID userId);

    List<UserProfile> findByUserIdIn(List<UUID> userIds);

    /** Nhóm ứng viên "khám phá" cho người dùng ít quan hệ: ưu tiên người hoạt động gần đây và có nhiều bạn. */
    @Query("SELECT p FROM UserProfile p WHERE p.userId NOT IN :excluded ORDER BY p.lastActiveAt DESC NULLS LAST, p.friendCount DESC")
    List<UserProfile> findDiscoveryCandidates(@Param("excluded") java.util.Collection<UUID> excluded, Pageable pageable);

    /** Thành viên mới nhất: chưa có hoạt động nào nên không lọt vào nhóm "hoạt động gần đây", nhưng rất đáng được gợi ý. */
    @Query("SELECT p FROM UserProfile p WHERE p.userId NOT IN :excluded ORDER BY p.createdAt DESC")
    List<UserProfile> findNewestMembers(@Param("excluded") java.util.Collection<UUID> excluded, Pageable pageable);

    @Query("SELECT p FROM UserProfile p WHERE " +
           "LOWER(COALESCE(p.firstName, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(COALESCE(p.lastName, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(COALESCE(p.middleName, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(CONCAT(COALESCE(p.lastName, ''), ' ', COALESCE(p.middleName, ''), ' ', COALESCE(p.firstName, ''))) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<UserProfile> searchUsers(@Param("query") String query, Pageable pageable);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE UserProfile p SET p.profileViewCount = COALESCE(p.profileViewCount, 0) + 1 WHERE p.userId = :userId AND p.deleted = false")
    int incrementProfileViewCount(@Param("userId") UUID userId);
}
