package ng.com.nawill.pay.onboarding.team;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamInvitationRepository extends JpaRepository<TeamInvitation, UUID> {

    List<TeamInvitation> findByBusinessId(UUID businessId);

    Optional<TeamInvitation> findByToken(String token);

    Optional<TeamInvitation> findByIdAndBusinessId(UUID id, UUID businessId);
}
