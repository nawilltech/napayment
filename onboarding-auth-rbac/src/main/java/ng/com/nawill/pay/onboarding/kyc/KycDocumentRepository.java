package ng.com.nawill.pay.onboarding.kyc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KycDocumentRepository extends JpaRepository<KycDocument, UUID> {

    List<KycDocument> findByBusinessId(UUID businessId);

    Optional<KycDocument> findByBusinessIdAndDocumentType(UUID businessId, KycDocumentType documentType);
}
