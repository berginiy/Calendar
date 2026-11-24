package com.example.officebooking.common;

import java.util.List;
import org.springframework.http.HttpStatus;

/** Ожидаемая бизнес-ошибка с кодом, понятным сообщением и HTTP-статусом. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final List<String> details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    public ApiException(HttpStatus status, String code, String message, List<String> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public List<String> getDetails() { return details; }
}
