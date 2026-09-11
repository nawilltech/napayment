package ng.com.nawill.pay.onboarding.rbac;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByNameAndBusinessIdIsNull(String name);

    Optional<Role> findByBusinessIdAndName(UUID businessId, String name);

    Page<Role> findByBusinessId(UUID businessId, Pageable pageable);

    Page<Role> findByBusinessIdAndNameContainingIgnoreCase(UUID businessId, String term, Pageable pageable);
}
