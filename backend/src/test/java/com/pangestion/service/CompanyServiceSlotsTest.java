package com.pangestion.service;

import com.pangestion.dto.CompanyDto;
import com.pangestion.exception.BusinessException;
import com.pangestion.exception.NotFoundException;
import com.pangestion.model.Company;
import com.pangestion.repository.CompanyRepository;
import com.pangestion.repository.CustomerRepository;
import com.pangestion.repository.OrderRepository;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Franjas de recogida, la regla de negocio central de los pedidos.
 * Test de unidad: los repositorios son mocks, no hay base de datos.
 */
@Tag("reglas-negocio") // S17: entra en el CI
@ExtendWith(MockitoExtension.class)
@DisplayName("Unidad · CompanyService (franjas)")
class CompanyServiceSlotsTest {

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    @Mock
    CompanyRepository companyRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    CustomerRepository customerRepository;
    @Mock
    OrderRepository orderRepository;
    @Mock
    CurrentUser currentUser;
    @InjectMocks
    CompanyService companyService;

    private Company company;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(1L);
        company.setName("Forn");
        company.setOpeningTime(LocalTime.of(8, 0));
        company.setClosingTime(LocalTime.of(10, 0));
        company.setSlotMinutes(30);
        company.setOrdersPerSlot(2);
        company.setActive(true);
        lenient().when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        lenient().when(orderRepository.countInSlot(any(), any(), any(), any())).thenReturn(0L);
    }

    @Test
    @DisplayName("genera una franja cada slotMinutes desde la apertura hasta antes del cierre")
    void slotTimes() {
        List<CompanyDto.Slot> slots = companyService.getSlots(1L, TOMORROW);

        assertThat(slots).extracting(CompanyDto.Slot::time).containsExactly(
                LocalTime.of(8, 0), LocalTime.of(8, 30), LocalTime.of(9, 0), LocalTime.of(9, 30));
        assertThat(slots).allMatch(CompanyDto.Slot::available).allMatch(s -> s.remaining() == 2);
    }

    @Test
    @DisplayName("una franja con el aforo completo sale como no disponible")
    void fullSlot() {
        when(orderRepository.countInSlot(company, TOMORROW, LocalTime.of(9, 0), -1L)).thenReturn(2L);

        CompanyDto.Slot nine = companyService.getSlots(1L, TOMORROW).get(2);

        assertThat(nine.available()).isFalse();
        assertThat(nine.remaining()).isZero();
    }

    @Test
    @DisplayName("las franjas de un día pasado no están disponibles")
    void pastDay() {
        assertThat(companyService.getSlots(1L, LocalDate.now().minusDays(1)))
                .noneMatch(CompanyDto.Slot::available);
    }

    @Test
    @DisplayName("si la empresa no acepta pedidos, ninguna franja está disponible")
    void inactiveCompany() {
        company.setActive(false);

        assertThat(companyService.getSlots(1L, TOMORROW)).noneMatch(CompanyDto.Slot::available);
    }

    @Test
    @DisplayName("empresa que no existe: NotFoundException")
    void unknownCompany() {
        when(companyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.getSlots(99L, TOMORROW)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("validar: una franja libre de mañana es válida")
    void validSlot() {
        assertThatCode(() -> companyService.validateSlot(company, TOMORROW, LocalTime.of(8, 30), -1L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validar: empresa sin aceptar pedidos")
    void validateInactive() {
        company.setActive(false);

        assertThatThrownBy(() -> companyService.validateSlot(company, TOMORROW, LocalTime.of(8, 30), -1L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("no acepta pedidos");
    }

    @Test
    @DisplayName("validar: una hora que no es franja (fuera de horario o descuadrada)")
    void validateNotASlot() {
        assertThatThrownBy(() -> companyService.validateSlot(company, TOMORROW, LocalTime.of(10, 0), -1L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("no es una franja");
        assertThatThrownBy(() -> companyService.validateSlot(company, TOMORROW, LocalTime.of(8, 15), -1L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("no es una franja");
    }

    @Test
    @DisplayName("validar: una franja que ya ha pasado")
    void validatePast() {
        assertThatThrownBy(() -> companyService.validateSlot(company, LocalDate.now().minusDays(1), LocalTime.of(8, 0), -1L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ya ha pasado");
    }

    @Test
    @DisplayName("validar: una franja completa")
    void validateFull() {
        when(orderRepository.countInSlot(company, TOMORROW, LocalTime.of(8, 0), -1L)).thenReturn(2L);

        assertThatThrownBy(() -> companyService.validateSlot(company, TOMORROW, LocalTime.of(8, 0), -1L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("completa");
    }

    @Test
    @DisplayName("validar: al editar un pedido, ese mismo pedido no cuenta para el aforo")
    void validateExcludesEditedOrder() {
        when(orderRepository.countInSlot(eq(company), eq(TOMORROW), eq(LocalTime.of(8, 0)), eq(5L))).thenReturn(1L);

        assertThatCode(() -> companyService.validateSlot(company, TOMORROW, LocalTime.of(8, 0), 5L))
                .doesNotThrowAnyException();
    }
}
