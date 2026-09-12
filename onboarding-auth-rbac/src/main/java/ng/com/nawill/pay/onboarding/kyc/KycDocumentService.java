package ng.com.nawill.pay.onboarding.kyc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import ng.com.nawill.pay.common.exception.BadRequestException;
import ng.com.nawill.pay.common.exception.ForbiddenException;
import ng.com.nawill.pay.common.exception.ResourceNotFoundException;
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
    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
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
            throw new BadRequestException("Missing file");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BadRequestException("File must be 10MB or smaller");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BadRequestException("Only PDF, PNG, or JPEG files are accepted");
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
        CurrentUser currentUser = currentUserResolver.requireBusinessScope();
        return kycDocumentRepository.findByBusinessId(currentUser.businessId());
    }

    @Transactional(readOnly = true)
    public LoadedFile download(UUID documentId) {
        CurrentUser currentUser = currentUserResolver.requireBusinessScope();
        KycDocument document = kycDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
        if (!document.getBusinessId().equals(currentUser.businessId())) {
            throw new ForbiddenException("Document does not belong to the caller's business");
        }
        return fileStorageGateway.load(document.getStorageKey());
    }
}
