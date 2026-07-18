package com.vetspace.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.vetspace.config.MediaProperties;
import org.junit.jupiter.api.Test;

class HtmlSanitizerTest {

    private static final String MEDIA_BASE = "http://localhost:9000/vetspace";

    private final HtmlSanitizer sanitizer = newSanitizer();

    private static HtmlSanitizer newSanitizer() {
        MediaProperties properties = new MediaProperties();
        properties.setPublicBaseUrl(MEDIA_BASE);
        return new HtmlSanitizer(properties);
    }

    @Test
    void stripsScriptTagsEntirely() {
        String result = sanitizer.sanitize("<p>ok</p><script>alert('xss')</script>");
        assertThat(result).isEqualTo("<p>ok</p>");
        assertThat(result).doesNotContain("script", "alert");
    }

    @Test
    void stripsStyleTagsEntirely() {
        String result = sanitizer.sanitize("<style>p{display:none}</style><p>visible</p>");
        assertThat(result).doesNotContain("style", "display");
        assertThat(result).contains("<p>visible</p>");
    }

    @Test
    void stripsEventHandlerAttributes() {
        String result = sanitizer.sanitize(
            "<img src=\"" + MEDIA_BASE + "/media/a.png\" alt=\"x\" onerror=\"steal()\">"
                + "<p onclick=\"evil()\">text</p>");
        assertThat(result).doesNotContain("onerror", "onclick", "steal", "evil");
        assertThat(result).contains("<img src=\"" + MEDIA_BASE + "/media/a.png\" alt=\"x\">");
        assertThat(result).contains("<p>text</p>");
    }

    @Test
    void keepsSpanColorStyleOnly() {
        String result = sanitizer.sanitize(
            "<span style=\"color: red; position: fixed; font-size: 99px\">warn</span>");
        assertThat(result).isEqualTo("<span style=\"color: red\">warn</span>");
    }

    @Test
    void dropsStyleAttributeWhenNoColorRemains() {
        String result = sanitizer.sanitize("<span style=\"position: fixed\">x</span>");
        assertThat(result).isEqualTo("<span>x</span>");
    }

    @Test
    void rejectsDangerousColorValues() {
        String result = sanitizer.sanitize("<span style=\"color: url('https://evil.example')\">x</span>");
        assertThat(result).isEqualTo("<span>x</span>");
    }

    @Test
    void keepsImagesFromOurMediaDomainOnly() {
        String ours = "<img src=\"" + MEDIA_BASE + "/media/ok.png\" alt=\"fine\">";
        String result = sanitizer.sanitize(ours + "<img src=\"https://evil.example/x.png\" alt=\"nope\">");
        assertThat(result).isEqualTo(ours);
    }

    @Test
    void rejectsImageOnSameHostButDifferentPathPrefix() {
        String result = sanitizer.sanitize("<img src=\"http://localhost:9000/otherbucket/x.png\">");
        assertThat(result).isEmpty();
    }

    @Test
    void anchorsRequireHttpsAndGetRelNoopener() {
        String result = sanitizer.sanitize(
            "<a href=\"https://example.com/doc\">ok</a><a href=\"http://insecure.example\">bad</a>"
                + "<a href=\"javascript:alert(1)\">worse</a>");
        assertThat(result).contains("<a href=\"https://example.com/doc\" rel=\"noopener\">ok</a>");
        assertThat(result).doesNotContain("insecure.example", "javascript");
    }

    @Test
    void keepsAllowedFormattingTags() {
        String input = "<p>a</p><br><b>b</b><strong>s</strong><i>i</i><em>e</em><u>u</u>"
            + "<ul><li>1</li></ul><ol><li>2</li></ol><h3>t3</h3><h4>t4</h4>";
        assertThat(sanitizer.sanitize(input)).isEqualTo(input);
    }

    @Test
    void dropsDisallowedTagsButKeepsTheirText() {
        String result = sanitizer.sanitize("<div><table><tr><td>cell</td></tr></table><h1>big</h1></div>");
        assertThat(result).doesNotContain("<div", "<table", "<h1");
        assertThat(result).contains("cell", "big");
    }

    @Test
    void nullAndBlankPassThrough() {
        assertThat(sanitizer.sanitize(null)).isNull();
        assertThat(sanitizer.sanitize("")).isEmpty();
    }
}
