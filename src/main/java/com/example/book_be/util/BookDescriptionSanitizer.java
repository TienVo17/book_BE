package com.example.book_be.util;

public final class BookDescriptionSanitizer {
    private BookDescriptionSanitizer() {
    }

    public static String sanitizeRichText(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        String sanitized = value;
        sanitized = sanitized.replaceAll("(?is)<script.*?>.*?</script>", "");
        sanitized = sanitized.replaceAll("(?is)<style.*?>.*?</style>", "");
        sanitized = sanitized.replaceAll("(?i)on\\w+\\s*=\\s*\"[^\"]*\"", "");
        sanitized = sanitized.replaceAll("(?i)on\\w+\\s*=\\s*'[^']*'", "");
        sanitized = sanitized.replaceAll("(?i)on\\w+\\s*=\\s*[^\\s>]+", "");
        sanitized = sanitized.replaceAll("(?i)javascript:", "");
        return sanitized.trim();
    }

    public static String summarizePlainText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return value;
        }

        String plainText = value.replaceAll("(?is)<[^>]*>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (plainText.length() <= maxLength) {
            return plainText;
        }
        return plainText.substring(0, maxLength).trim() + "...";
    }
}
