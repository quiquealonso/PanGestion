package com.pangestion.service;

import com.pangestion.dto.UserDto;
import com.pangestion.exception.BusinessException;
import com.pangestion.exception.NotFoundException;
import com.pangestion.model.BusinessType;
import com.pangestion.model.Role;
import com.pangestion.model.User;
import com.pangestion.repository.UserRepository;
import com.pangestion.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Usuarios del panel.
 * - SUPERADMIN: ve y gestiona todos, y elige la empresa de cada uno.
 * - ADMIN: solo los de su empresa, y no puede crear SUPERADMIN.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final CompanyService companyService;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public List<UserDto.Response> findAll() {
        List<User> users = currentUser.isSuperAdmin()
                ? userRepository.findAllByOrderByNameAsc()
                : userRepository.findAllByCompanyIdOrderByNameAsc(currentUser.companyId());
        return users.stream().map(UserDto.Response::from).toList();
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

    public UserDto.Response update(Long id, UserDto.Request request) {
        User user = getEntity(id);
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(request.email(), id)) {
            throw new BusinessException("Ya existe un usuario con el email " + request.email());
        }
        boolean losesRole = user.isActive() && (user.getRole() != request.role() || !request.active());
        if (losesRole && user.getEmail().equalsIgnoreCase(currentUser.email())) {
            throw new BusinessException("No puedes cambiarte el rol ni desactivarte a ti mismo");
        }
        if (losesRole) {
            ensureAnotherAdmin(user);
        }
        apply(user, request);
        return UserDto.Response.from(user);
    }

    public void delete(Long id) {
        User user = getEntity(id);
        if (user.getEmail().equalsIgnoreCase(currentUser.email())) {
            throw new BusinessException("No puedes eliminar tu propio usuario");
        }
        if (user.isActive()) {
            ensureAnotherAdmin(user);
        }
        userRepository.delete(user);
    }

    /** Cada empresa debe conservar un ADMIN activo, y la aplicacion un SUPERADMIN activo. */
    private void ensureAnotherAdmin(User user) {
        if (user.getRole() == Role.SUPERADMIN && userRepository.countByRoleAndActiveTrue(Role.SUPERADMIN) <= 1) {
            throw new BusinessException("Debe quedar al menos un superadministrador activo");
        }
        if (user.getRole() == Role.ADMIN && user.getCompany() != null
                && userRepository.countByCompanyIdAndRoleAndActiveTrue(user.getCompany().getId(), Role.ADMIN) <= 1) {
            throw new BusinessException("La empresa debe conservar al menos un administrador activo");
        }
    }

    /** Un ADMIN solo puede tocar usuarios de su empresa. */
    User getEntity(Long id) {
        var user = currentUser.isSuperAdmin()
                ? userRepository.findById(id)
                : userRepository.findByIdAndCompanyId(id, currentUser.companyId());
        return user.orElseThrow(() -> new NotFoundException("Usuario", id));
    }

    private void apply(User user, UserDto.Request request) {
        if (currentUser.isSuperAdmin()) {
            if (request.role() == Role.SUPERADMIN) {
                user.setCompany(null);
            } else if (request.companyId() == null) {
                throw new BusinessException("Elige la empresa del usuario");
            } else {
                user.setCompany(companyService.getEntity(request.companyId()));
            }
        } else {
            if (request.role() == Role.SUPERADMIN) {
                throw new BusinessException("No puedes crear superadministradores");
            }
            user.setCompany(companyService.current());
        }
        if ((request.role() == Role.CAMARERO || request.role() == Role.COCINA)
                && user.getCompany().getType() == BusinessType.PANADERIA) {
            throw new BusinessException("Los roles camarero y cocina solo existen en bares y restaurantes");
        }
        user.setName(request.name().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setRole(request.role());
        user.setActive(request.active());
        if (request.password() != null && !request.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }
    }
}
