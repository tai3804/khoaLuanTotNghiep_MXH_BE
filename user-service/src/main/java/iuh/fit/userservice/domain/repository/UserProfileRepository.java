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

    @Query("SELECT p FROM UserProfile p WHERE " +
           "LOWER(COALESCE(p.firstName, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(COALESCE(p.lastName, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(COALESCE(p.middleName, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(CONCAT(COALESCE(p.lastName, ''), ' ', COALESCE(p.middleName, ''), ' ', COALESCE(p.firstName, ''))) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<UserProfile> searchUsers(@Param("query") String query, Pageable pageable);
}
