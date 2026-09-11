package ng.com.nawill.pay.onboarding.kyc;

import java.time.Instant;
import java.util.UUID;

public record KycDocumentResponse(
        UUID id,
        KycDocumentType type,
        String fileName,
        long sizeBytes,
        Instant uploadedAt
) {

    public static KycDocumentResponse from(KycDocument document) {
        return new KycDocumentResponse(document.getId(), document.getDocumentType(), document.getFileName(),
                document.getSizeBytes(), document.getCreatedAt());
    }
}
