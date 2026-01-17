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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (message.contains("out of stock")) {
            ApiError error = new ApiError("OUT_OF_STOCK", "Out of stock.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
        if (message.contains("own product")) {
            ApiError error = new ApiError("OWN_PRODUCT", "You cannot perform this action on your own listing.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }
        if (message.contains("product not found")) {
            ApiError error = new ApiError("PRODUCT_NOT_FOUND", "Product not found.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
        ApiError error = new ApiError("INVALID_REQUEST", "Request could not be processed.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
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
