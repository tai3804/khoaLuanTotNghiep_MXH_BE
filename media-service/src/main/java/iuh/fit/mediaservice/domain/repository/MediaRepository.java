package iuh.fit.mediaservice.domain.repository;

import iuh.fit.mediaservice.domain.entities.Media;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MediaRepository extends JpaRepository<Media, UUID> {

    Page<Media> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<Media> findByFileKey(String fileKey);

    void deleteByFileKey(String fileKey);

    @Query("SELECT COALESCE(SUM(m.fileSize), 0) FROM Media m WHERE m.userId = :userId")
    long sumFileSizeByUserId(@Param("userId") UUID userId);
}
