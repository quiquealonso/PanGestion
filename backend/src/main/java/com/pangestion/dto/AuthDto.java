package com.pangestion.dto;

import jakarta.validation.constraints.NotBlank;

public final class AuthDto {

    private AuthDto() {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record LoginResponse(String token, long expiresInMinutes, UserDto.Response user) {
    }
}
