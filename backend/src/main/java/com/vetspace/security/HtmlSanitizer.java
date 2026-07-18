package com.vetspace.security;

import com.vetspace.config.MediaProperties;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Server-side sanitizer for all admin-authored rich HTML (proposition explanations, notes).
 * Allowlist from CLAUDE.md: p, br, b, strong, i, em, u, span[style with color only],
 * ul, ol, li, h3, h4, img[src from our media domain only, alt], a[href https only, rel=noopener].
 * Everything else — scripts, styles, event handlers, foreign images — is stripped, never escaped-through.
 */
@Component
public class HtmlSanitizer {

    /** Conservative charset for CSS color values; blocks url(), expression() and anything else exotic. */
    private static final Pattern SAFE_COLOR_VALUE = Pattern.compile("^[#a-zA-Z0-9(),.%\\s-]+$");

    private final String mediaBaseUrl;

    public HtmlSanitizer(MediaProperties mediaProperties) {
        this.mediaBaseUrl = trimTrailingSlash(mediaProperties.getPublicBaseUrl());
    }

    public String sanitize(String html) {
        if (html == null) {
            return null;
        }
        Safelist safelist = Safelist.none()
            .addTags("p", "br", "b", "strong", "i", "em", "u", "span", "ul", "ol", "li", "h3", "h4", "img", "a")
            .addAttributes("span", "style")
            .addAttributes("img", "src", "alt")
            .addAttributes("a", "href", "rel")
            // Absolute-URL protocols; relative URLs fail these checks and lose the attribute.
            .addProtocols("a", "href", "https")
            .addProtocols("img", "src", "http", "https");

        Document dirty = Jsoup.parseBodyFragment(html);
        Document doc = new Cleaner(safelist).clean(dirty);
        doc.outputSettings().prettyPrint(false);

        for (Element span : doc.select("span[style]")) {
            String colorOnly = keepOnlyColor(span.attr("style"));
            if (colorOnly.isEmpty()) {
                span.removeAttr("style");
            } else {
                span.attr("style", colorOnly);
            }
        }
        // Images may only come from our own media storage; anything else is removed outright.
        for (Element img : doc.select("img")) {
            if (!img.attr("src").startsWith(mediaBaseUrl + "/")) {
                img.remove();
            }
        }
        for (Element a : doc.select("a")) {
            a.attr("rel", "noopener");
        }
        return doc.body().html();
    }

    private String keepOnlyColor(String style) {
        return Arrays.stream(style.split(";"))
            .map(String::trim)
            .filter(decl -> {
                int colon = decl.indexOf(':');
                if (colon <= 0) {
                    return false;
                }
                String property = decl.substring(0, colon).trim();
                String value = decl.substring(colon + 1).trim();
                return property.equalsIgnoreCase("color") && SAFE_COLOR_VALUE.matcher(value).matches();
            })
            .collect(Collectors.joining("; "));
    }

    private static String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
