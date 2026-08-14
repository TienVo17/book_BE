package com.example.book_be.shared.web;

import com.example.book_be.nguoidung.session.RefreshSessionException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import com.example.book_be.danhgia.web.ReviewImageValidationException;
import com.example.book_be.sach.service.SachNotFoundException;
import com.example.book_be.sach.service.StockAdjustmentConflictException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final ApiErrorWriter errorWriter;

    public ApiExceptionHandler(ApiErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @ExceptionHandler(RefreshSessionException.class)
    public ResponseEntity<ApiError> handleRefreshSession(RefreshSessionException exception,
                                                         HttpServletRequest request) {
        ApiError error = errorWriter.create(
                request,
                HttpStatus.UNAUTHORIZED.value(),
                exception.getCode(),
                "Phiên đăng nhập không hợp lệ.");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(org.springframework.http.HttpHeaders.SET_COOKIE,
                        "__Host-refresh=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=Lax")
                .body(error);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException exception,
                                                          HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        HttpStatus safeStatus = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        return response(request, safeStatus, codeFor(safeStatus),
                safeMessage(exception.getReason(), defaultMessage(safeStatus)));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class,
            BindException.class,
            MissingRequestValueException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestPartException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(Exception exception, HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Yêu cầu không hợp lệ.");
    }

    /** Multipart hong cu phap la loi request; vuot dung luong co handler 413 rieng ben duoi. */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiError> handleMultipart(MultipartException exception,
                                                    HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST,
                "INVALID_MULTIPART", "Dữ liệu tải lên không hợp lệ.");
    }

    @ExceptionHandler(SachNotFoundException.class)
    public ResponseEntity<ApiError> handleBookNotFound(SachNotFoundException exception,
                                                        HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND, "BOOK_NOT_FOUND", exception.getMessage());
    }

    /**
     * Thieu cau hinh luu tru la loi VAN HANH, va nguoi van hanh can biet thieu bien nao.
     * Nhanh {@code IllegalStateException} ben duoi vut bo message, nen phai co kieu rieng.
     */
    @ExceptionHandler(StorageNotConfiguredException.class)
    public ResponseEntity<ApiError> handleStorageNotConfigured(StorageNotConfiguredException exception,
                                                                HttpServletRequest request) {
        log.error("event=storage_not_configured traceId={} path={}",
                RequestTraceFilter.currentTraceId(request), request.getRequestURI());
        return response(request, HttpStatus.SERVICE_UNAVAILABLE,
                "STORAGE_NOT_CONFIGURED", exception.getMessage());
    }

    /** Moi nguyen nhan mot ma rieng, de giao dien noi dung cai nguoi dung can sua. */
    @ExceptionHandler(ReviewImageValidationException.class)
    public ResponseEntity<ApiError> handleReviewImage(ReviewImageValidationException exception,
                                                       HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST,
                exception.getMa().name(), exception.getMessage());
    }

    /**
     * Tep vuot nguong cua container. Khong co handler thi no roi vao handleUnexpected va
     * tra 500 kem mot dong log moi request — vua sai hop dong vua la moi lam ngap log.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(MaxUploadSizeExceededException exception,
                                                          HttpServletRequest request) {
        return response(request, HttpStatus.PAYLOAD_TOO_LARGE,
                "FILE_TOO_LARGE", "Tệp tải lên vượt quá dung lượng cho phép.");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleUnavailable(IllegalStateException exception,
                                                       HttpServletRequest request) {
        return response(request, HttpStatus.SERVICE_UNAVAILABLE,
                "SERVICE_UNAVAILABLE", "Dịch vụ phụ trợ chưa sẵn sàng.");
    }

    /** Image storage I/O is an upstream failure, not a server defect: keep it a 503. */
    @ExceptionHandler(java.io.IOException.class)
    public ResponseEntity<ApiError> handleUpstreamIo(java.io.IOException exception,
                                                      HttpServletRequest request) {
        return response(request, HttpStatus.SERVICE_UNAVAILABLE,
                "UPSTREAM_IO_ERROR", "Không thể xử lý tệp lúc này, vui lòng thử lại.");
    }

    @ExceptionHandler(StockAdjustmentConflictException.class)
    public ResponseEntity<ApiError> handleStockConflict(StockAdjustmentConflictException exception,
                                                         HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "STOCK_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleConflict(DataIntegrityViolationException exception,
                                                    HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "DATA_CONFLICT", "Dữ liệu đang xung đột.");
    }

    /**
     * Spring Data REST nem exception nay khi mot repository resource khong duoc expose.
     * Khong co handler thi no roi vao handleUnexpected va tra ve 500 INTERNAL_ERROR — sai
     * ca ma lan y nghia: tai nguyen khong ton tai la 404, khong phai loi may chu.
     *
     * <p>Lo hong nay co san tu truoc (vi du /sach/{id}/listHinhAnh, do HinhAnhRepository
     * da la exported = false), chi lo ra khi dong not association cua danh gia.
     */
    @ExceptionHandler(org.springframework.data.rest.webmvc.ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleRestResourceNotFound(
            org.springframework.data.rest.webmvc.ResourceNotFoundException exception,
            HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy tài nguyên.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception,
                                                            HttpServletRequest request) {
        return response(request, HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED", "Phương thức HTTP không được hỗ trợ.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException exception,
                                                      HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy tài nguyên.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("event=api_exception traceId={} method={} path={} type={}",
                RequestTraceFilter.currentTraceId(request), request.getMethod(), request.getRequestURI(),
                exception.getClass().getSimpleName());
        return response(request, HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR", "Máy chủ không thể xử lý yêu cầu.");
    }

    private ResponseEntity<ApiError> response(HttpServletRequest request, HttpStatus status,
                                              String code, String message) {
        return ResponseEntity.status(status).body(errorWriter.create(request, status.value(), code, message));
    }

    private String codeFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "VALIDATION_ERROR";
            case UNAUTHORIZED -> "UNAUTHENTICATED";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case CONFLICT -> "CONFLICT";
            case TOO_MANY_REQUESTS -> "RATE_LIMITED";
            // A dependency being down is not the same failure class as a server
            // defect, and clients retry the two differently.
            case SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE";
            case METHOD_NOT_ALLOWED -> "METHOD_NOT_ALLOWED";
            case PAYLOAD_TOO_LARGE -> "FILE_TOO_LARGE";
            default -> status.is5xxServerError() ? "INTERNAL_ERROR" : "REQUEST_FAILED";
        };
    }

    private String defaultMessage(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Yêu cầu không hợp lệ.";
            case UNAUTHORIZED -> "Vui lòng đăng nhập để tiếp tục.";
            case FORBIDDEN -> "Bạn không có quyền thực hiện thao tác này.";
            case NOT_FOUND -> "Không tìm thấy tài nguyên.";
            case CONFLICT -> "Yêu cầu xung đột với trạng thái hiện tại.";
            default -> status.is5xxServerError()
                    ? "Máy chủ không thể xử lý yêu cầu."
                    : "Không thể xử lý yêu cầu.";
        };
    }

    private String safeMessage(String candidate, String fallback) {
        if (candidate == null || candidate.isBlank() || candidate.length() > 500) {
            return fallback;
        }
        return candidate;
    }
}
