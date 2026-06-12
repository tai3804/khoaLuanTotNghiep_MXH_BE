package iuh.fit.graduationthesis.auth.repositories;

import iuh.fit.graduationthesis.auth.modules.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByUserName(String userName);

    boolean existsByUserName(String userName);
}
