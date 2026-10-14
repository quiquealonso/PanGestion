package com.pangestion.controller;

import com.pangestion.dto.UserDto;
import com.pangestion.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Gestion de usuarios. Solo ADMIN (ver SecurityConfig). */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public List<UserDto.Response> findAll() {
        return userService.findAll();
    }

    @GetMapping("/{id}")
    public UserDto.Response findById(@PathVariable Long id) {
        return userService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto.Response create(@Valid @RequestBody UserDto.Request request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    public UserDto.Response update(@PathVariable Long id, @Valid @RequestBody UserDto.Request request,
                                   @AuthenticationPrincipal Jwt jwt) {
        return userService.update(id, request, jwt.getSubject());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        userService.delete(id, jwt.getSubject());
    }
}
