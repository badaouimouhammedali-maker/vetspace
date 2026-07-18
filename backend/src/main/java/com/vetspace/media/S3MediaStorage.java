package com.vetspace.media;

import com.vetspace.config.MediaProperties;
import java.net.URI;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class S3MediaStorage implements MediaStorage {

    private final S3Client s3Client;
    private final MediaProperties properties;

    public S3MediaStorage(MediaProperties properties) {
        this.properties = properties;
        // Path-style + fixed region: required for minio, harmless for R2 (which is region-agnostic).
        this.s3Client = S3Client.builder()
            .endpointOverride(URI.create(properties.getEndpoint()))
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
            .forcePathStyle(true)
            .build();
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .contentType(contentType)
                .build(),
            RequestBody.fromBytes(bytes));
    }
}
