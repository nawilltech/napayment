package ng.com.nawill.pay.onboarding.rbac;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByNameAndBusinessIdIsNull(String name);

    List<Role> findByBusinessId(UUID businessId);
}
