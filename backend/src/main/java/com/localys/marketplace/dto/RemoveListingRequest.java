package com.localys.marketplace.dto;

public record RemoveListingRequest(
        String reason,
        String note
) {
}
