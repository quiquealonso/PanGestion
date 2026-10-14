package com.pangestion.dto;

import com.pangestion.model.Role;
import com.pangestion.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class UserDto {

    private UserDto() {
    }

    /** password es obligatoria al crear; al editar, si llega vacia se mantiene la actual. */
    public record Request(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 120) String email,
            @Size(min = 6, max = 100) String password,
            @NotNull Role role,
            boolean active) {
    }

    public record Response(Long id, String name, String email, Role role, boolean active, LocalDateTime lastLogin) {

        public static Response from(User user) {
            return new Response(user.getId(), user.getName(), user.getEmail(), user.getRole(),
                    user.isActive(), user.getLastLogin());
        }
    }
}
