package ng.com.nawill.pay.common.storage;

import java.io.InputStream;

/** The bytes and content-type for a previously-stored file, as returned by {@link FileStorageGateway#load}. */
public record LoadedFile(InputStream content, String contentType, long sizeBytes) {
}
