package com.pangestion.service;

import com.pangestion.dto.UserDto;
import com.pangestion.exception.BusinessException;
import com.pangestion.model.Company;
import com.pangestion.model.Role;
import com.pangestion.model.User;
import com.pangestion.repository.UserRepository;
import com.pangestion.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Reglas de los usuarios: nadie se queda sin administrador y nadie se bloquea a si mismo. */
@Tag("reglas-negocio") // S17: entra en el CI
@ExtendWith(MockitoExtension.class)
@DisplayName("Unidad · UserService (reglas)")
class UserServiceRulesTest {

    @Mock
    UserRepository userRepository;
    @Mock
    CompanyService companyService;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    CurrentUser currentUser;
    @InjectMocks
    UserService userService;

    private Company company;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(1L);
        lenient().when(currentUser.isSuperAdmin()).thenReturn(false);
        lenient().when(currentUser.companyId()).thenReturn(1L);
        lenient().when(currentUser.email()).thenReturn("yo@test.es");
        lenient().when(companyService.current()).thenReturn(company);
        lenient().when(passwordEncoder.encode(any())).thenAnswer(i -> "hash:" + i.getArgument(0));
    }

    private User existing(long id, String email, Role role) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setName("Usuario");
        user.setRole(role);
        user.setActive(true);
        user.setCompany(role == Role.SUPERADMIN ? null : company);
        lenient().when(userRepository.findByIdAndCompanyId(id, 1L)).thenReturn(Optional.of(user));
        lenient().when(userRepository.findById(id)).thenReturn(Optional.of(user));
        return user;
    }

    private static UserDto.Request request(String email, String password, Role role, boolean active, Long companyId) {
        return new UserDto.Request("Nombre", email, password, role, active, companyId);
    }

    @Test
    @DisplayName("crear: guarda la contraseña cifrada, el email en minúsculas y la empresa del ADMIN")
    void createEncodesPassword() {
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        userService.create(request(" Nuevo@Test.ES ", "secreto1", Role.GESTOR, true, null));

        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(u ->
                u.getPassword().equals("hash:secreto1") && u.getEmail().equals("nuevo@test.es")
                        && u.getCompany() == company));
    }

    @Test
    @DisplayName("crear: la contraseña es obligatoria")
    void createRequiresPassword() {
        assertThatThrownBy(() -> userService.create(request("a@test.es", " ", Role.GESTOR, true, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("contrasena es obligatoria");
    }

    @Test
    @DisplayName("crear: email repetido")
    void createDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("a@test.es")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request("a@test.es", "secreto1", Role.GESTOR, true, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Ya existe");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("un ADMIN no puede crear superadministradores")
    void adminCannotCreateSuperAdmin() {
        assertThatThrownBy(() -> userService.create(request("a@test.es", "secreto1", Role.SUPERADMIN, true, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("superadministradores");
    }

    @Test
    @DisplayName("el SUPERADMIN tiene que elegir la empresa de un usuario que no es superadmin")
    void superAdminMustChooseCompany() {
        when(currentUser.isSuperAdmin()).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request("a@test.es", "secreto1", Role.ADMIN, true, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Elige la empresa");
    }

    @Test
    @DisplayName("editar: si la contraseña llega vacía se mantiene la actual")
    void updateKeepsPassword() {
        User user = existing(2L, "gestor@test.es", Role.GESTOR);
        user.setPassword("hash:antigua");

        userService.update(2L, request("gestor@test.es", null, Role.GESTOR, true, null));

        assertThat(user.getPassword()).isEqualTo("hash:antigua");
    }

    @Test
    @DisplayName("editar: nadie puede cambiarse el rol ni desactivarse a sí mismo")
    void cannotDemoteYourself() {
        existing(3L, "yo@test.es", Role.ADMIN);

        assertThatThrownBy(() -> userService.update(3L, request("yo@test.es", null, Role.GESTOR, true, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("a ti mismo");
        assertThatThrownBy(() -> userService.update(3L, request("yo@test.es", null, Role.ADMIN, false, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("a ti mismo");
    }

    @Test
    @DisplayName("editar: la empresa debe conservar al menos un ADMIN activo")
    void keepsOneAdmin() {
        existing(4L, "otro-admin@test.es", Role.ADMIN);
        when(userRepository.countByCompanyIdAndRoleAndActiveTrue(1L, Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.update(4L, request("otro-admin@test.es", null, Role.GESTOR, true, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("al menos un administrador");
    }

    @Test
    @DisplayName("editar: con otro ADMIN activo sí se puede cambiar el rol")
    void demoteWhenAnotherAdminExists() {
        User user = existing(4L, "otro-admin@test.es", Role.ADMIN);
        when(userRepository.countByCompanyIdAndRoleAndActiveTrue(1L, Role.ADMIN)).thenReturn(2L);

        userService.update(4L, request("otro-admin@test.es", null, Role.GESTOR, true, null));

        assertThat(user.getRole()).isEqualTo(Role.GESTOR);
    }

    @Test
    @DisplayName("borrar: no puedes borrar tu propio usuario")
    void cannotDeleteYourself() {
        existing(3L, "yo@test.es", Role.ADMIN);

        assertThatThrownBy(() -> userService.delete(3L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("propio usuario");
    }

    @Test
    @DisplayName("borrar: debe quedar al menos un SUPERADMIN activo")
    void keepsOneSuperAdmin() {
        when(currentUser.isSuperAdmin()).thenReturn(true);
        existing(5L, "super2@test.es", Role.SUPERADMIN);
        when(userRepository.countByRoleAndActiveTrue(Role.SUPERADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.delete(5L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("superadministrador");
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("borrar: un usuario inactivo se puede borrar aunque sea el último ADMIN")
    void deleteInactiveAdmin() {
        User user = existing(6L, "inactivo@test.es", Role.ADMIN);
        user.setActive(false);

        userService.delete(6L);

        verify(userRepository).delete(user);
    }
}
