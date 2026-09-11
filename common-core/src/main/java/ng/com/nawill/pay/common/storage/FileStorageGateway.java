package ng.com.nawill.pay.common.storage;

import java.io.InputStream;

/**
 * Stores/retrieves arbitrary uploaded files (KYC documents today, potentially
 * avatars/other attachments later), keyed by an opaque {@code storageKey} the
 * caller persists on its own entity - never a public URL, since most of what
 * goes through this is sensitive (KYC documents in particular). {@code
 * ownerId} scopes the key namespace (e.g. a businessId) so two owners can
 * never collide or guess each other's keys.
 * <p>
 * {@link LocalFileStorageGateway} is the only implementation today - local
 * disk, fine for a single-instance dev/demo deployment. Swap in an
 * S3-compatible implementation (AWS S3, MinIO, Cloudinary) behind this same
 * interface for production; no call site changes needed, same posture as
 * {@code PaymentProcessorGateway}/{@code SettlementGateway}.
 */
public interface FileStorageGateway {

    StoredFile store(String ownerId, String fileName, String contentType, InputStream content);

    LoadedFile load(String storageKey);

    void delete(String storageKey);
}
