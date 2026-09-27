package ng.com.nawill.pay.onboarding.kyc;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import ng.com.nawill.pay.common.crypto.EncryptionService;
import ng.com.nawill.pay.common.exception.BadRequestException;
import ng.com.nawill.pay.common.logging.PiiMasker;
import ng.com.nawill.pay.common.security.CurrentUser;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import ng.com.nawill.pay.onboarding.audit.AuditEventType;
import ng.com.nawill.pay.onboarding.audit.AuditOutcome;
import ng.com.nawill.pay.onboarding.audit.SecurityAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-8: BVN/NIN verification for the business owner. */
@Service
@Transactional
public class OwnerIdentityService {

    private static final Logger log = LoggerFactory.getLogger(OwnerIdentityService.class);
    private static final Pattern ELEVEN_DIGITS = Pattern.compile("^\\d{11}$");

    private final OwnerIdentityRepository ownerIdentityRepository;
    private final IdentityVerificationGateway identityVerificationGateway;
    private final EncryptionService encryptionService;
    private final SecurityAuditService securityAuditService;
    private final CurrentUserResolver currentUserResolver;

    public OwnerIdentityService(OwnerIdentityRepository ownerIdentityRepository,
                                 IdentityVerificationGateway identityVerificationGateway,
                                 EncryptionService encryptionService, SecurityAuditService securityAuditService,
                                 CurrentUserResolver currentUserResolver) {
        this.ownerIdentityRepository = ownerIdentityRepository;
        this.identityVerificationGateway = identityVerificationGateway;
        this.encryptionService = encryptionService;
        this.securityAuditService = securityAuditService;
        this.currentUserResolver = currentUserResolver;
    }

    public OwnerIdentityResponse save(OwnerIdentityRequest request) {
        CurrentUser currentUser = currentUserResolver.requireBusinessScope();
        validate(request);
        boolean hasBvn = request.bvn() != null && !request.bvn().isBlank();

        IdentityType type = hasBvn ? IdentityType.BVN : IdentityType.NIN;
        String number = hasBvn ? request.bvn() : request.nin();
        VerificationResult result = identityVerificationGateway.verify(type, number);

        OwnerIdentity identity = ownerIdentityRepository.findByBusinessId(currentUser.businessId())
                .orElseGet(() -> new OwnerIdentity(currentUser.businessId()));
        String bvnEncrypted = hasBvn ? encryptionService.encrypt(request.bvn()) : null;
        String ninEncrypted = request.nin() != null && !request.nin().isBlank() ? encryptionService.encrypt(request.nin()) : null;
        identity.apply(bvnEncrypted, ninEncrypted, result);
        identity = ownerIdentityRepository.save(identity);

        log.info("owner identity saved: businessId={} type={} number={} verified={}",
                currentUser.businessId(), type, PiiMasker.maskKeepLast4(number), result.verified());
        securityAuditService.record(AuditEventType.OWNER_IDENTITY_SUBMITTED,
                result.verified() ? AuditOutcome.SUCCESS : AuditOutcome.FAILURE, currentUser.userId(),
                currentUser.businessId(), type + " " + PiiMasker.maskKeepLast4(number) + ", verified=" + result.verified());

        return toResponse(identity);
    }

    @Transactional(readOnly = true)
    public OwnerIdentityResponse get() {
        CurrentUser currentUser = currentUserResolver.requireBusinessScope();
        return ownerIdentityRepository.findByBusinessId(currentUser.businessId())
                .map(this::toResponse)
                .orElse(null);
    }

    private void validate(OwnerIdentityRequest request) {
        boolean hasBvn = request.bvn() != null && !request.bvn().isBlank();
        boolean hasNin = request.nin() != null && !request.nin().isBlank();
        if (!hasBvn && !hasNin) {
            throw new BadRequestException("Provide either a BVN or an NIN");
        }
        if (hasBvn && !ELEVEN_DIGITS.matcher(request.bvn()).matches()) {
            throw new BadRequestException("BVN must be 11 digits");
        }
        if (hasNin && !ELEVEN_DIGITS.matcher(request.nin()).matches()) {
            throw new BadRequestException("NIN must be 11 digits");
        }
    }

    /**
     * Platform KYC review: any business's owner identity, with BVN/NIN masked
     * to the last 4 digits - reviewers need the verification outcome, not the
     * full number. Authorisation is enforced by the caller.
     */
    @Transactional(readOnly = true)
    public Optional<OwnerIdentityResponse> findMaskedForBusiness(UUID businessId) {
        return ownerIdentityRepository.findByBusinessId(businessId)
                .map(identity -> new OwnerIdentityResponse(masked(identity.getBvnEncrypted()),
                        masked(identity.getNinEncrypted()), identity.isVerified()));
    }

    private OwnerIdentityResponse toResponse(OwnerIdentity identity) {
        return new OwnerIdentityResponse(decrypt(identity.getBvnEncrypted()), decrypt(identity.getNinEncrypted()),
                identity.isVerified());
    }

    private String decrypt(String encrypted) {
        return encrypted == null ? null : encryptionService.decrypt(encrypted);
    }

    /** Absent stays absent - only a number that was actually provided gets masked. */
    private String masked(String encrypted) {
        return encrypted == null ? null : PiiMasker.maskKeepLast4(decrypt(encrypted));
    }
}
