package ng.com.nawill.pay.onboarding.auth;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, UUID> {

    List<PasswordHistory> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<PasswordHistory> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
