package ng.com.nawill.pay.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Dev/demo-scale {@link FileStorageGateway}: writes under a configured
 * directory on local disk. A single-line {@code .meta} sidecar file next to
 * each upload carries its content-type (the only piece {@link #load} needs
 * that isn't recoverable from the bytes themselves) - see the interface
 * Javadoc for why this isn't the production-shape implementation.
 */
@Service
public class LocalFileStorageGateway implements FileStorageGateway {

    private final Path baseDir;

    public LocalFileStorageGateway(@Value("${nawill.storage.local.base-dir}") String baseDir) {
        // Normalized eagerly so resolve()'s startsWith(baseDir) check below
        // compares two consistently-normalized paths - an un-normalized
        // "./x" base and a normalize()-d target never startsWith() each
        // other even when the target is legitimately inside the base.
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
    }

    @Override
    public StoredFile store(String ownerId, String fileName, String contentType, InputStream content) {
        try {
            String safeName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
            String storageKey = ownerId + "/" + UUID.randomUUID() + "-" + safeName;
            Path target = resolve(storageKey);
            Files.createDirectories(target.getParent());
            long sizeBytes = Files.copy(content, target);
            Files.writeString(metaPath(target), contentType, StandardCharsets.UTF_8);
            return new StoredFile(storageKey, sizeBytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file for owner " + ownerId, e);
        }
    }

    @Override
    public LoadedFile load(String storageKey) {
        try {
            Path target = resolve(storageKey);
            String contentType = Files.exists(metaPath(target))
                    ? Files.readString(metaPath(target), StandardCharsets.UTF_8)
                    : "application/octet-stream";
            return new LoadedFile(Files.newInputStream(target), contentType, Files.size(target));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load file: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Path target = resolve(storageKey);
            Files.deleteIfExists(target);
            Files.deleteIfExists(metaPath(target));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete file: " + storageKey, e);
        }
    }

    /**
     * Resolves a storage key strictly within {@code baseDir} - rejects any
     * key that would escape it (e.g. via {@code ../}) since the key's
     * filename segment is caller-influenced (the uploaded file's name).
     */
    private Path resolve(String storageKey) {
        Path target = baseDir.resolve(storageKey).normalize();
        if (!target.startsWith(baseDir)) {
            throw new IllegalArgumentException("Invalid storage key: " + storageKey);
        }
        return target;
    }

    private Path metaPath(Path target) {
        return target.resolveSibling(target.getFileName() + ".meta");
    }
}
