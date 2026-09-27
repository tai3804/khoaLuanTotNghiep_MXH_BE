package iuh.fit.aiservice.domain.repository;

import iuh.fit.aiservice.domain.entities.SensitiveKeyword;
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
public interface SensitiveKeywordRepository extends JpaRepository<SensitiveKeyword, UUID> {

    Optional<SensitiveKeyword> findByKeyword(String keyword);

    List<SensitiveKeyword> findByStatus(String status);

    Page<SensitiveKeyword> findByIsAutoLearned(boolean isAutoLearned, Pageable pageable);

    @Query("SELECT s FROM SensitiveKeyword s WHERE " +
           "(:keyword IS NULL OR LOWER(s.keyword) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
           "(:category IS NULL OR s.category = :category) AND " +
           "(:status IS NULL OR s.status = :status)")
    Page<SensitiveKeyword> searchKeywords(
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("status") String status,
            Pageable pageable
    );

    long countByIsAutoLearnedTrue();
    long countByStatus(String status);
}
