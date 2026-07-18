package com.vetspace.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Server-side verification against Google reCAPTCHA. Fully bypassed when app.recaptcha.enabled=false (dev/tests). */
@Component
public class RecaptchaVerifier {

    private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    private final RecaptchaProperties properties;
    private final RestClient restClient = RestClient.create();

    public RecaptchaVerifier(RecaptchaProperties properties) {
        this.properties = properties;
    }

    public boolean verify(String token) {
        if (!properties.isEnabled()) {
            return true;
        }
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("secret", properties.getSecret());
            form.add("response", token);
            SiteVerifyResponse result = restClient.post()
                .uri(VERIFY_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(SiteVerifyResponse.class);
            return result != null && result.success();
        } catch (RestClientException ex) {
            return false;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SiteVerifyResponse(boolean success) {
    }
}
