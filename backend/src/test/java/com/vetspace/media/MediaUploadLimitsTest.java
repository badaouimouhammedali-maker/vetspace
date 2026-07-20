package com.vetspace.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.vetspace.config.MediaProperties;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * The content-image cap is not just a product choice: uploads pass through the Vercel
 * edge proxy that fronts the API, so exceeding its request-body limit fails at the edge
 * with an opaque error instead of the app's 413. Pin the number down.
 */
class MediaUploadLimitsTest {

    private static final long MB = 1024 * 1024;

    private final MediaService service = new MediaService(mock(MediaStorage.class), mock(MediaProperties.class));

    /** Size is checked before the bytes are read, so a sparse array is enough. */
    private MockMultipartFile fileOf(long bytes) {
        return new MockMultipartFile("file", "x.png", "image/png", new byte[(int) bytes]);
    }

    @Test
    void contentImagesAreCappedAtFourMegabytes() {
        assertThat(MediaService.Kind.CONTENT.maxBytes).isEqualTo(4 * MB);
    }

    @Test
    void profilePhotosStayCappedAtTwoMegabytes() {
        assertThat(MediaService.Kind.PROFILE.maxBytes).isEqualTo(2 * MB);
    }

    @Test
    void rejectsAContentImageOverTheCapWith413() {
        assertThatThrownBy(() -> service.upload(fileOf(4 * MB + 1), MediaService.Kind.CONTENT))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> {
                ResponseStatusException ex = (ResponseStatusException) e;
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
                // The message must quote the limit actually enforced.
                assertThat(ex.getReason()).contains("4MB");
            });
    }

    @Test
    void rejectsAProfilePhotoOverItsSmallerCap() {
        assertThatThrownBy(() -> service.upload(fileOf(2 * MB + 1), MediaService.Kind.PROFILE))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    @Test
    void rejectsAnEmptyUpload() {
        assertThatThrownBy(() -> service.upload(fileOf(0), MediaService.Kind.CONTENT))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
