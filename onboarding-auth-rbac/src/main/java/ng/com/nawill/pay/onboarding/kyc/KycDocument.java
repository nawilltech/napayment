package ng.com.nawill.pay.onboarding.kyc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import ng.com.nawill.pay.common.entity.BaseEntity;

/** FR-8: one uploaded KYC document per (business, {@link KycDocumentType}). */
@Entity
@Table(name = "kyc_documents")
public class KycDocument extends BaseEntity {

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 32)
    private KycDocumentType documentType;

    @Column(name = "file_name", nullable = false, length = 256)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    protected KycDocument() {
    }

    public KycDocument(UUID businessId, KycDocumentType documentType, String fileName, String contentType,
                        long sizeBytes, String storageKey) {
        this.businessId = businessId;
        this.documentType = documentType;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public KycDocumentType getDocumentType() {
        return documentType;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void replace(String fileName, String contentType, long sizeBytes, String storageKey) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
    }
}
