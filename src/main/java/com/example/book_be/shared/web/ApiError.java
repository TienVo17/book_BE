package com.example.book_be.shared.web;

import java.time.Instant;

/** Stable error response for controller-owned APIs and Spring Security denials. */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String traceId
) {
}
