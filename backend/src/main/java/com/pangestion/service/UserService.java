package com.pangestion.service;

import com.pangestion.dto.UserDto;
import com.pangestion.exception.BusinessException;
import com.pangestion.exception.NotFoundException;
import com.pangestion.model.Role;
import com.pangestion.model.User;
import com.pangestion.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UserDto.Response> findAll() {
        return userRepository.findAllByOrderByNameAsc().stream().map(UserDto.Response::from).toList();
    }

    @Transactional(readOnly = true)
    public UserDto.Response findById(Long id) {
        return UserDto.Response.from(getEntity(id));
    }

    public UserDto.Response create(UserDto.Request request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BusinessException("Ya existe un usuario con el email " + request.email());
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new BusinessException("La contrasena es obligatoria para un usuario nuevo");
        }
        User user = new User();
        apply(user, request);
        return UserDto.Response.from(userRepository.save(user));
    }

    public UserDto.Response update(Long id, UserDto.Request request, String currentEmail) {
        User user = getEntity(id);
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(request.email(), id)) {
            throw new BusinessException("Ya existe un usuario con el email " + request.email());
        }
        boolean losesAdmin = user.getRole() == Role.ADMIN && user.isActive()
                && (request.role() != Role.ADMIN || !request.active());
        if (losesAdmin) {
            if (user.getEmail().equalsIgnoreCase(currentEmail)) {
                throw new BusinessException("No puedes quitarte el rol de administrador ni desactivarte");
            }
            ensureAnotherAdmin();
        }
        apply(user, request);
        return UserDto.Response.from(user);
    }

    public void delete(Long id, String currentEmail) {
        User user = getEntity(id);
        if (user.getEmail().equalsIgnoreCase(currentEmail)) {
            throw new BusinessException("No puedes eliminar tu propio usuario");
        }
        if (user.getRole() == Role.ADMIN && user.isActive()) {
            ensureAnotherAdmin();
        }
        userRepository.delete(user);
    }

    private void ensureAnotherAdmin() {
        if (userRepository.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw new BusinessException("Debe quedar al menos un administrador activo");
        }
    }

    User getEntity(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("Usuario", id));
    }

    private void apply(User user, UserDto.Request request) {
        user.setName(request.name().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setRole(request.role());
        user.setActive(request.active());
        if (request.password() != null && !request.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }
    }
}
