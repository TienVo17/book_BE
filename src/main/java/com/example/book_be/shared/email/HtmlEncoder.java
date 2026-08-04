package com.example.book_be.shared.email;

import org.springframework.web.util.HtmlUtils;

public final class HtmlEncoder {
    private HtmlEncoder() {
    }

    public static String encode(Object value) {
        return HtmlUtils.htmlEscape(value == null ? "" : String.valueOf(value));
    }
}
