package iuh.fit.adminservice.infrastructure.persistence;

import iuh.fit.adminservice.domain.entities.BlacklistedWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JpaBlacklistedWordRepository extends JpaRepository<BlacklistedWord, Long> {
    Optional<BlacklistedWord> findByWordIgnoreCase(String word);
    boolean existsByWordIgnoreCase(String word);
}
