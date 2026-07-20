package com.vetspace.media;

import com.vetspace.config.MediaProperties;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * The single entry point for images into the system. The uploaded bytes are typed by magic-byte
 * sniffing only — the client's filename and declared Content-Type are never trusted.
 */
@Service
public class MediaService {

    /**
     * Upload policies: content images 4MB (gif allowed), profile photos 2MB jpeg/png/webp.
     * Content images are capped at 4MB rather than 5 so uploads stay under the body limit of
     * the Vercel edge proxy that fronts the API (see docs/deploy.md).
     */
    public enum Kind {
        CONTENT(4L * 1024 * 1024, Set.of(ImageType.JPEG, ImageType.PNG, ImageType.WEBP, ImageType.GIF), "media"),
        PROFILE(2L * 1024 * 1024, Set.of(ImageType.JPEG, ImageType.PNG, ImageType.WEBP), "profile");

        final long maxBytes;
        final Set<ImageType> allowed;
        final String keyPrefix;

        Kind(long maxBytes, Set<ImageType> allowed, String keyPrefix) {
            this.maxBytes = maxBytes;
            this.allowed = allowed;
            this.keyPrefix = keyPrefix;
        }
    }

    private final MediaStorage storage;
    private final MediaProperties properties;

    public MediaService(MediaStorage storage, MediaProperties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    public String upload(MultipartFile file, Kind kind) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file provided");
        }
        if (file.getSize() > kind.maxBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                "File exceeds the " + (kind.maxBytes / (1024 * 1024)) + "MB limit");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unreadable upload");
        }
        ImageType type = sniff(bytes);
        if (type == null || !kind.allowed.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported file type");
        }
        String key = kind.keyPrefix + "/" + UUID.randomUUID() + "." + type.extension;
        storage.put(key, bytes, type.contentType);
        return properties.getPublicBaseUrl() + "/" + key;
    }

    private enum ImageType {
        JPEG("jpg", "image/jpeg"),
        PNG("png", "image/png"),
        WEBP("webp", "image/webp"),
        GIF("gif", "image/gif");

        final String extension;
        final String contentType;

        ImageType(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }
    }

    private ImageType sniff(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return ImageType.JPEG;
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
            && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A) {
            return ImageType.PNG;
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
            && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return ImageType.WEBP;
        }
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8'
            && (b[4] == '7' || b[4] == '9') && b[5] == 'a') {
            return ImageType.GIF;
        }
        return null;
    }
}
