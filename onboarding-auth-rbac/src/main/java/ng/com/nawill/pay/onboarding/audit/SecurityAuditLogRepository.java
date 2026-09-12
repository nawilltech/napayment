package ng.com.nawill.pay.onboarding.audit;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Save and read only - no update/delete method exists here on purpose, see
 * {@link SecurityAuditLog}'s Javadoc.
 */
public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditLog, UUID> {
}
