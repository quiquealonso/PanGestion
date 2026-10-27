package com.pangestion.controller;

import com.pangestion.dto.UserDto;
import com.pangestion.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Gestion de usuarios: SUPERADMIN (todos) y ADMIN (los de su empresa). Ver SecurityConfig. */
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

    /** 201 con la cabecera Location del recurso creado. */
    @PostMapping
    public ResponseEntity<UserDto.Response> create(@Valid @RequestBody UserDto.Request request) {
        UserDto.Response created = userService.create(request);
        return ResponseEntity.created(Locations.child(created.id())).body(created);
    }

    @PutMapping("/{id}")
    public UserDto.Response update(@PathVariable Long id, @Valid @RequestBody UserDto.Request request) {
        return userService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        userService.delete(id);
    }
}
