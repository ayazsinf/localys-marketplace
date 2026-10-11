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

    /** Refunds and payouts the payment provider refused or could not be asked for. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (message.contains("refund") || message.contains("stripe") || message.contains("payout")) {
            ApiError error = new ApiError("PAYMENT_PROVIDER_ERROR", "The payment provider could not process this. Please try again later.");
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
        }
        throw ex;
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
        if (message.contains("renewed recently") || message.contains("only online listings can be renewed")) {
            ApiError error = new ApiError("RENEW_NOT_ALLOWED", "A listing can be renewed once a week while it is online.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
        if (message.equals("user is blocked")) {
            ApiError error = new ApiError("USER_BLOCKED", "You cannot contact this user.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }
        if (message.equals("cannot block yourself")) {
            ApiError error = new ApiError("INVALID_REQUEST", "You cannot block yourself.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
        if (message.equals("offer not found")) {
            ApiError error = new ApiError("OFFER_NOT_FOUND", "Offer not found.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
        if (message.contains("offer already open")) {
            ApiError error = new ApiError("OFFER_ALREADY_OPEN", "You already have an open offer on this listing.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
        if (message.contains("offer cannot be answered") || message.contains("not accepted or has expired")) {
            ApiError error = new ApiError("OFFER_STATE_CONFLICT", "This offer is no longer open for this action.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
        if (message.contains("offer must be below") || message.contains("offer must be at least")
                || message.contains("counter offer must be")
                || message.contains("offer amount must be")) {
            ApiError error = new ApiError("INVALID_OFFER_AMOUNT", ex.getMessage() + ".");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
        if (message.equals("seller is away")) {
            ApiError error = new ApiError("SELLER_AWAY", "The seller is away at the moment.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
        if (message.contains("vacation message is too long")) {
            ApiError error = new ApiError("VALIDATION_ERROR", "The away message can be at most 300 characters.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
        if (message.equals("dispute not found")) {
            ApiError error = new ApiError("DISPUTE_NOT_FOUND", "Dispute not found.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
        if (message.contains("dispute not allowed") || message.contains("dispute already")) {
            ApiError error = new ApiError("DISPUTE_STATE_CONFLICT", "This is not possible in the current state of the dispute or item.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
        if (message.startsWith("invalid dispute") || message.startsWith("dispute description")
                || message.startsWith("dispute response") || message.startsWith("dispute note")) {
            ApiError error = new ApiError("INVALID_DISPUTE", ex.getMessage() + ".");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
        if (message.contains("hand delivery only")) {
            ApiError error = new ApiError("HAND_DELIVERY_ONLY", "This item is only handed over in person; contact the seller.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
        if (message.contains("invalid item condition") || message.contains("at least one delivery option")) {
            ApiError error = new ApiError("INVALID_LISTING", ex.getMessage() + ".");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
        if (message.equals("image not found")) {
            ApiError error = new ApiError("IMAGE_NOT_FOUND", "Image not found.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
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
        if (message.contains("rating must be") || message.contains("review comment is too long")) {
            ApiError error = new ApiError("INVALID_REVIEW", "Rating must be 1 to 5 and the comment at most 1000 characters.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
        if (message.contains("review not allowed")) {
            ApiError error = new ApiError("REVIEW_NOT_ALLOWED", "You can review an item once you have received it.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
        if (message.contains("already reviewed")) {
            ApiError error = new ApiError("ALREADY_REVIEWED", "You already reviewed this item.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
        if (message.contains("invalid report reason") || message.contains("report details are")) {
            ApiError error = new ApiError("INVALID_REPORT", "Choose a reason; details are required for \"other\" and limited to 1000 characters.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
        if (message.contains("already reported")) {
            ApiError error = new ApiError("ALREADY_REPORTED", "You already reported this listing.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
        if (message.contains("report not found")) {
            ApiError error = new ApiError("REPORT_NOT_FOUND", "Report not found.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
        if (message.contains("report already resolved")) {
            ApiError error = new ApiError("REPORT_RESOLVED", "This report was already handled.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
        if (message.contains("saved search limit reached")) {
            ApiError error = new ApiError("SAVED_SEARCH_LIMIT", "You can save up to 20 searches.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
        if (message.contains("saved search not found")) {
            ApiError error = new ApiError("SAVED_SEARCH_NOT_FOUND", "Saved search not found.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
        if (message.startsWith("saved search")) {
            ApiError error = new ApiError("INVALID_SAVED_SEARCH", ex.getMessage() + ".");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
        if (message.equals("user not found")) {
            ApiError error = new ApiError("USER_NOT_FOUND", "User not found.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
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
