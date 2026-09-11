package ng.com.nawill.pay.common.storage;

/** What a {@link FileStorageGateway#store} call hands back to persist alongside a record. */
public record StoredFile(String storageKey, long sizeBytes) {
}
