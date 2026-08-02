package com.vetspace.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;

/**
 * What we record about the device a refresh-token family was minted from.
 *
 * <p>Derived entirely server-side from headers the client cannot usefully lie about for
 * its own benefit — a forged User-Agent only mislabels the attacker's own row in the
 * admin list. Nothing here is a security control; it exists so that "your session was
 * closed" can be explained, and so an account shared across a class group shows up as a
 * pattern before anyone is accused of it.
 */
public record DeviceContext(String deviceLabel, String ip) {

    private static final DeviceContext UNKNOWN = new DeviceContext(null, null);

    /** Longest value the column accepts; a hostile User-Agent is truncated, not rejected. */
    private static final int MAX_LABEL_LENGTH = 120;

    public static DeviceContext unknown() {
        return UNKNOWN;
    }

    public static DeviceContext from(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        return new DeviceContext(labelFor(request.getHeader("User-Agent")), clientIp(request));
    }

    /**
     * "Chrome sur Windows" — browser plus platform, in French, and nothing else.
     *
     * <p>Order matters: Edge and Opera both carry "Chrome" in their User-Agent, and
     * Chrome carries "Safari", so the more specific brands have to be tested first or
     * every browser on earth reads as Chrome. Version numbers are deliberately dropped;
     * they change weekly and would make yesterday's session look like a different device.
     */
    static String labelFor(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        String browser = browserOf(ua);
        String platform = platformOf(ua);
        if (browser == null && platform == null) {
            return null;
        }
        if (platform == null) {
            return browser;
        }
        if (browser == null) {
            return platform;
        }
        return truncate(browser + " sur " + platform);
    }

    private static String browserOf(String ua) {
        if (ua.contains("edg/") || ua.contains("edga/") || ua.contains("edgios/")) {
            return "Edge";
        }
        if (ua.contains("opr/") || ua.contains("opera")) {
            return "Opera";
        }
        if (ua.contains("samsungbrowser")) {
            return "Samsung Internet";
        }
        if (ua.contains("firefox") || ua.contains("fxios")) {
            return "Firefox";
        }
        if (ua.contains("chrome") || ua.contains("crios")) {
            return "Chrome";
        }
        // Safari last: every WebKit-based browser above also advertises "Safari".
        if (ua.contains("safari")) {
            return "Safari";
        }
        return null;
    }

    private static String platformOf(String ua) {
        if (ua.contains("android")) {
            return "Android";
        }
        // iPad's desktop-class UA says "macintosh", so the iOS checks come first.
        if (ua.contains("iphone")) {
            return "iPhone";
        }
        if (ua.contains("ipad")) {
            return "iPad";
        }
        if (ua.contains("windows")) {
            return "Windows";
        }
        if (ua.contains("mac os x") || ua.contains("macintosh")) {
            return "macOS";
        }
        if (ua.contains("linux")) {
            return "Linux";
        }
        return null;
    }

    /**
     * The client IP as this deployment can best determine it.
     *
     * <p>Railway (and any reverse proxy) puts the real address in X-Forwarded-For and
     * makes {@code getRemoteAddr()} the proxy's own address, which would make every
     * student in the country share one IP and destroy the sharing signal entirely. The
     * first entry is the originating client; the rest are intermediate proxies.
     *
     * <p>This header is client-controllable, so it is trusted for ANALYTICS ONLY — it
     * feeds an admin's "distinct IPs" count and nothing that grants or denies access.
     * Treating it as a security input would need a trusted-proxy allowlist.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = ipOrNull(forwarded.split(",")[0].trim());
            if (first != null) {
                return first;
            }
        }
        return ipOrNull(request.getRemoteAddr());
    }

    /** Longest textual IPv6 is 39 characters; the column allows 45. */
    private static final int MAX_IP_LENGTH = 45;

    /**
     * Keeps anything that is not IP-shaped out of the column.
     *
     * <p>X-Forwarded-For is client-controlled, so this value is whatever a caller cares to
     * send. A syntactic check — length, plus hex digits and separators only — is enough:
     * it rejects hostnames, oversized junk and anything that could confuse a later
     * consumer, without a DNS lookup and without letting a hostile header turn someone's
     * own login into an error.
     */
    private static String ipOrNull(String candidate) {
        if (candidate == null || candidate.isEmpty() || candidate.length() > MAX_IP_LENGTH) {
            return null;
        }
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            boolean allowed = (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                || c == '.' || c == ':';
            if (!allowed) {
                return null;
            }
        }
        return candidate;
    }

    private static String truncate(String value) {
        return value.length() <= MAX_LABEL_LENGTH ? value : value.substring(0, MAX_LABEL_LENGTH);
    }
}
