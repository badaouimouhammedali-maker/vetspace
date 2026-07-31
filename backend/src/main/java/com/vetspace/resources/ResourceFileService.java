package com.vetspace.resources;

import com.vetspace.config.MediaProperties;
import com.vetspace.domain.content.FileType;
import com.vetspace.media.MediaStorage;
import java.io.IOException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Puts study-resource files into blob storage and takes them out again.
 *
 * <p>Same discipline as {@code MediaService}: the bytes are typed by sniffing their
 * header, never by the filename or the client's {@code Content-Type}. A file called
 * {@code cours.pdf} whose first bytes are {@code MZ} is a Windows executable, and the
 * only place that is caught is here.
 *
 * <p>Separate from MediaService rather than a fifth {@code Kind} because the two differ
 * in more than policy: this one accepts PDFs, returns the stored size and type for the
 * database row, and can delete. Bolting that onto an enum whose contract is
 * "bytes in, URL out" would have made both harder to read.
 */
@Service
public class ResourceFileService {

    /** Everything lives under this prefix, which is also how a key is recovered from a URL. */
    static final String KEY_PREFIX = "resources/";

    private final MediaStorage storage;
    private final MediaProperties properties;
    private final long maxBytes;

    public ResourceFileService(MediaStorage storage, MediaProperties properties,
                                @Value("${app.resources.max-file-size-mb:25}") long maxFileSizeMb) {
        this.storage = storage;
        this.properties = properties;
        this.maxBytes = maxFileSizeMb * 1024 * 1024;
    }

    /** What was stored: the public URL, the sniffed type, and the true byte count. */
    public record StoredFile(String url, FileType fileType, long sizeBytes) {
    }

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file provided");
        }
        if (file.getSize() > maxBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                "File exceeds the " + (maxBytes / (1024 * 1024)) + "MB limit");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unreadable upload");
        }
        Sniffed sniffed = sniff(bytes);
        if (sniffed == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Unsupported file type — only PDF, JPEG, PNG and WebP are accepted");
        }
        // Random key: the uploaded filename never reaches storage, so it cannot carry a
        // path traversal, an extension mismatch, or somebody's name.
        String key = KEY_PREFIX + UUID.randomUUID() + "." + sniffed.extension;
        storage.put(key, bytes, sniffed.contentType);
        // bytes.length, not file.getSize(): the length actually written is what the size
        // column should report.
        return new StoredFile(publicUrl(key), sniffed.fileType, bytes.length);
    }

    /** Removes the stored object behind a resource URL. */
    public void deleteByUrl(String fileUrl) {
        String key = keyFromUrl(fileUrl);
        if (key != null) {
            storage.delete(key);
        }
    }

    /**
     * Recovers the storage key from a stored URL.
     *
     * <p>Anchored on {@link #KEY_PREFIX} rather than on the configured base URL, because
     * the base URL is a moving target — R2 public URLs change when a custom domain is
     * attached, and rows written before the change would otherwise become undeletable.
     * The last occurrence wins, so a base URL that itself contains "resources/" cannot
     * truncate the key.
     */
    static String keyFromUrl(String fileUrl) {
        if (fileUrl == null) {
            return null;
        }
        int at = fileUrl.lastIndexOf(KEY_PREFIX);
        return at < 0 ? null : fileUrl.substring(at);
    }

    private String publicUrl(String key) {
        return properties.getPublicBaseUrl() + "/" + key;
    }

    private record Sniffed(FileType fileType, String extension, String contentType) {
    }

    private Sniffed sniff(byte[] b) {
        // %PDF- — the signature the PDF spec requires at the start of the file.
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') {
            return new Sniffed(FileType.PDF, "pdf", "application/pdf");
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return new Sniffed(FileType.IMAGE, "jpg", "image/jpeg");
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
            && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A) {
            return new Sniffed(FileType.IMAGE, "png", "image/png");
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
            && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return new Sniffed(FileType.IMAGE, "webp", "image/webp");
        }
        return null;
    }
}
