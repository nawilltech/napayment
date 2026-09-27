package ng.com.nawill.pay.onboarding.kyc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import ng.com.nawill.pay.common.exception.ApiException;
import ng.com.nawill.pay.common.exception.ErrorCode;
import ng.com.nawill.pay.common.security.CurrentUser;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import ng.com.nawill.pay.common.storage.FileStorageGateway;
import ng.com.nawill.pay.common.storage.LoadedFile;
import ng.com.nawill.pay.common.storage.StoredFile;
import ng.com.nawill.pay.onboarding.audit.AuditEventType;
import ng.com.nawill.pay.onboarding.audit.AuditOutcome;
import ng.com.nawill.pay.onboarding.audit.SecurityAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** FR-8: the 4 required business KYC document uploads. */
@Service
@Transactional
public class KycDocumentService {

    private static final Logger log = LoggerFactory.getLogger(KycDocumentService.class);
    private static final long BYTES_PER_MB = 1024 * 1024;
    private static final long MAX_SIZE_BYTES = 10 * BYTES_PER_MB;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("application/pdf", "image/png", "image/jpeg");

    private final KycDocumentRepository kycDocumentRepository;
    private final FileStorageGateway fileStorageGateway;
    private final SecurityAuditService securityAuditService;
    private final CurrentUserResolver currentUserResolver;

    public KycDocumentService(KycDocumentRepository kycDocumentRepository, FileStorageGateway fileStorageGateway,
                               SecurityAuditService securityAuditService, CurrentUserResolver currentUserResolver) {
        this.kycDocumentRepository = kycDocumentRepository;
        this.fileStorageGateway = fileStorageGateway;
        this.securityAuditService = securityAuditService;
        this.currentUserResolver = currentUserResolver;
    }

    public KycDocument upload(KycDocumentType type, MultipartFile file) {
        CurrentUser currentUser = currentUserResolver.requireBusinessScope();
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.FILE_REQUIRED);
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE, MAX_SIZE_BYTES / BYTES_PER_MB);
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new ApiException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }

        StoredFile stored;
        try {
            stored = fileStorageGateway.store(currentUser.businessId().toString(), file.getOriginalFilename(),
                    file.getContentType(), file.getInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }

        KycDocument document = kycDocumentRepository.findByBusinessIdAndDocumentType(currentUser.businessId(), type)
                .map(existing -> {
                    fileStorageGateway.delete(existing.getStorageKey());
                    existing.replace(file.getOriginalFilename(), file.getContentType(), stored.sizeBytes(), stored.storageKey());
                    return existing;
                })
                .orElseGet(() -> new KycDocument(currentUser.businessId(), type, file.getOriginalFilename(),
                        file.getContentType(), stored.sizeBytes(), stored.storageKey()));
        document = kycDocumentRepository.save(document);

        log.info("kyc document uploaded: businessId={} type={} sizeBytes={}",
                currentUser.businessId(), type, stored.sizeBytes());
        securityAuditService.record(AuditEventType.KYC_DOCUMENT_UPLOADED, AuditOutcome.SUCCESS, currentUser.userId(),
                currentUser.businessId(), type + " (" + stored.sizeBytes() + " bytes)");
        return document;
    }

    @Transactional(readOnly = true)
    public List<KycDocument> list() {
        return listForBusiness(currentUserResolver.requireBusinessScope().businessId());
    }

    /** Any business's documents - for platform KYC review; authorisation is enforced by the caller. */
    @Transactional(readOnly = true)
    public List<KycDocument> listForBusiness(UUID businessId) {
        return kycDocumentRepository.findByBusinessId(businessId);
    }

    @Transactional(readOnly = true)
    public LoadedFile download(UUID documentId) {
        CurrentUser currentUser = currentUserResolver.requireBusinessScope();
        KycDocument document = find(documentId);
        if (!document.getBusinessId().equals(currentUser.businessId())) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return fileStorageGateway.load(document.getStorageKey());
    }

    /** Platform KYC review: any business's document. Authorisation is enforced by the calling controller. */
    @Transactional(readOnly = true)
    public LoadedFile downloadForReview(UUID documentId) {
        return fileStorageGateway.load(find(documentId).getStorageKey());
    }

    private KycDocument find(UUID documentId) {
        return kycDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ApiException(ErrorCode.DOCUMENT_NOT_FOUND));
    }
}
