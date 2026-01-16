package com.localys.marketplace.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        ApiError error = new ApiError("USER_ALREADY_EXISTS", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String rawMessage = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        String message = rawMessage == null ? "" : rawMessage.toLowerCase();
        if (message.contains("sku") || message.contains("ukfhmd06dsmj6k0n90swsh8ie9g")) {
            ApiError error = new ApiError(
                    "SKU_ALREADY_EXISTS",
                    "SKU already exists. Use a different SKU or leave it blank to auto-generate."
            );
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
        ApiError error = new ApiError("DATA_INTEGRITY_VIOLATION", "Request could not be saved.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    public static class ApiError {
        private String code;
        private String message;

        public ApiError(String code, String message) {
            this.code = code;
            this.message = message;
        }
        public String getCode() { return code; }
        public String getMessage() { return message; }
    }
}
