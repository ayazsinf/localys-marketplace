package com.localys.marketplace.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        ApiError error = new ApiError("USER_ALREADY_EXISTS", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex) {
        ApiError error = new ApiError("BAD_CREDENTIALS", "Invalid credentials.");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<ApiError> handleRestClientResponse(RestClientResponseException ex) {
        if (ex.getStatusCode() == HttpStatus.CONFLICT) {
            ApiError error = new ApiError("USER_ALREADY_EXISTS", "Username or email already exists.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
        if (ex.getStatusCode().is4xxClientError()) {
            ApiError error = new ApiError("AUTH_PROVIDER_REJECTED", "Authentication provider rejected the request.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
        ApiError error = new ApiError("AUTH_PROVIDER_UNAVAILABLE", "Authentication provider is unavailable.");
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiError> handleRestClient(RestClientException ex) {
        ApiError error = new ApiError("AUTH_PROVIDER_UNAVAILABLE", "Authentication provider is unavailable.");
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "Request is invalid." : error.getDefaultMessage())
                .orElse("Request is invalid.");
        ApiError error = new ApiError("VALIDATION_ERROR", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
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
        if (message.contains("tracking number is required")) {
            ApiError error = new ApiError("TRACKING_REQUIRED", "A tracking number is required.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
        if (message.contains("cannot be shipped") || message.contains("cannot be confirmed")) {
            ApiError error = new ApiError("ORDER_STATE_CONFLICT", "This action is not possible for the order's current state.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
        if (message.contains("order not found")) {
            ApiError error = new ApiError("ORDER_NOT_FOUND", "Order not found.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
        if (message.contains("conversation not found")) {
            ApiError error = new ApiError("CONVERSATION_NOT_FOUND", "Conversation not found.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
        if (message.contains("cannot be reactivated")) {
            ApiError error = new ApiError(
                    "LISTING_NOT_REACTIVATABLE",
                    "Sold or removed listings cannot be reactivated. Please create a new listing."
            );
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
        if (message.contains("too many images")) {
            ApiError error = new ApiError("TOO_MANY_IMAGES", "A listing can have at most 10 images.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
        if (message.contains("image is too large")) {
            ApiError error = new ApiError("IMAGE_TOO_LARGE", "Images must be 10 MB or smaller.");
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
        }
        if (message.contains("only image uploads")) {
            ApiError error = new ApiError("INVALID_IMAGE", "Only JPEG, PNG, GIF and WebP images are allowed.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
        if (message.contains("not awaiting payment") || message.contains("no longer payable")) {
            ApiError error = new ApiError("ORDER_NOT_PAYABLE", "This order can no longer be paid.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
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
