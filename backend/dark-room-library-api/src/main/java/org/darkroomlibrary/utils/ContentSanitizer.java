package org.darkroomlibrary.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Centralized length rules and server-side content sanitization.
 */
public final class ContentSanitizer {

    public static final int MESSAGE_MAX_LENGTH = 1000;
    public static final int MESSAGE_REPLY_MAX_LENGTH = 1000;
    public static final int REVIEW_MAX_LENGTH = 1000;
    public static final int REVIEW_REPLY_MAX_LENGTH = 500;
    public static final int PROCUREMENT_MESSAGE_MAX_LENGTH = 1000;
    public static final int NOTICE_TITLE_MAX_LENGTH = 100;
    public static final int NOTICE_HTML_MAX_LENGTH = 20000;
    public static final int ATTACHMENT_NAME_MAX_LENGTH = 255;
    public static final int ATTACHMENT_URL_MAX_LENGTH = 500;

    private static final Pattern STORED_FILE_URL = Pattern.compile(
            "^/[-A-Za-z0-9_./]+/file/(?:getFile|public|download)\\?fileName="
                    + "[a-fA-F0-9]{32}\\.([a-z0-9]{2,5})$");
    private static final Set<String> MESSAGE_ATTACHMENT_TYPES = Set.of(
            "pdf", "doc", "docx", "jpg", "jpeg", "png", "gif", "bmp", "webp", "html", "htm");
    private static final Safelist NOTICE_SAFELIST = Safelist.relaxed()
            .addTags("s")
            .preserveRelativeLinks(true);

    private ContentSanitizer() {
    }

    public static String plainText(String input) {
        if (input == null) {
            return null;
        }
        return Jsoup.clean(input, Safelist.none())
                .replace("\u00A0", " ")
                .replace("\r\n", "\n")
                .trim();
    }

    public static String richText(String input) {
        if (input == null) {
            return null;
        }
        Document.OutputSettings outputSettings = new Document.OutputSettings().prettyPrint(false);
        return Jsoup.clean(
                input,
                "https://library.local/",
                NOTICE_SAFELIST,
                outputSettings
        ).trim();
    }

    public static boolean hasVisibleRichText(String html) {
        if (html == null || html.isBlank()) {
            return false;
        }
        Document document = Jsoup.parseBodyFragment(html);
        return !document.text().trim().isEmpty() || !document.select("img").isEmpty();
    }

    public static boolean exceedsLength(String value, int maxLength) {
        return value != null && value.codePointCount(0, value.length()) > maxLength;
    }

    public static boolean isSafeMessageAttachment(String url, String type) {
        if (url == null || url.isBlank() || type == null || type.isBlank()) {
            return false;
        }
        if (exceedsLength(url, ATTACHMENT_URL_MAX_LENGTH)) {
            return false;
        }
        Matcher matcher = STORED_FILE_URL.matcher(url.trim());
        if (!matcher.matches()) {
            return false;
        }
        String normalizedType = type.trim().toLowerCase(Locale.ROOT);
        return MESSAGE_ATTACHMENT_TYPES.contains(normalizedType)
                && normalizedType.equals(matcher.group(1));
    }
}
