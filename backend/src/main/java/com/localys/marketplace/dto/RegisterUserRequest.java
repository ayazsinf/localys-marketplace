package com.localys.marketplace.dto;

import com.localys.marketplace.model.enums.USER_ROLE;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @NotBlank
        @Size(min = 3, max = 50)
        String username,

        @NotBlank
        @Email
        String email,

        @NotBlank
        @Size(min = 6, max = 100)
        String password,

        USER_ROLE role
) {
}
