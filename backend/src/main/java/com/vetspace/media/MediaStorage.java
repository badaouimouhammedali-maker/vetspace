package com.vetspace.media;

/** Blob storage port; the only production implementation targets S3-compatible storage (R2/minio). */
public interface MediaStorage {

    /** Stores the bytes under the given key and returns nothing; the public URL is derived from configuration. */
    void put(String key, byte[] bytes, String contentType);
}
