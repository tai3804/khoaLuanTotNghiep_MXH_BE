package iuh.fit.adminservice.infrastructure.persistence;

import iuh.fit.adminservice.domain.entities.BlacklistedWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaBlacklistedWordRepository extends JpaRepository<BlacklistedWord, Long> {
}
