package com.pangestion.service;

import com.pangestion.dto.ServiceDto;
import com.pangestion.events.LiveEvent;
import com.pangestion.exception.BusinessException;
import com.pangestion.exception.NotFoundException;
import com.pangestion.model.BusinessType;
import com.pangestion.model.Company;
import com.pangestion.model.Payment;
import com.pangestion.model.PaymentMethod;
import com.pangestion.model.DiningTable;
import com.pangestion.model.ItemStatus;
import com.pangestion.model.Product;
import com.pangestion.model.ServiceLine;
import com.pangestion.model.Station;
import com.pangestion.model.TableSession;
import com.pangestion.repository.DiningAreaRepository;
import com.pangestion.repository.PaymentRepository;
import com.pangestion.repository.TableSessionRepository;
import com.pangestion.repository.UserRepository;
import com.pangestion.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Servicio de mesas de un bar o restaurante: abrir mesa con sus comensales, juntar o cambiar mesas,
 * pedir por rondas y liberar la mesa.
 * Reglas: una mesa solo puede estar en un servicio abierto; el comensal de cada producto existe en la mesa;
 * no se cambia nada de un servicio ya cerrado.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TableSessionService {

    private final TableSessionRepository sessionRepository;
    private final DiningAreaRepository areaRepository;
    private final DiningService diningService;
    private final ProductService productService;
    private final CompanyService companyService;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final ApplicationEventPublisher events;
    private final PaymentRepository paymentRepository;

    /** Plano de todas las zonas con el servicio abierto de cada mesa ocupada. */
    @Transactional(readOnly = true)
    public List<ServiceDto.FloorArea> floor() {
        Long companyId = currentUser.companyId();
        Map<Long, TableSession> openByTable = new HashMap<>();
        for (TableSession session : sessionRepository.findOpen(companyId)) {
            session.getTables().forEach(table -> openByTable.put(table.getId(), session));
        }
        return areaRepository.findAllByCompanyIdOrderBySortOrderAscNameAsc(companyId).stream()
                .map(area -> ServiceDto.FloorArea.from(area, openByTable))
                .toList();
    }

    @Transactional(readOnly = true)
    public ServiceDto.SessionResponse find(Long id) {
        return ServiceDto.SessionResponse.from(getEntity(id));
    }

    public ServiceDto.SessionResponse open(ServiceDto.OpenRequest request) {
        Company company = companyService.current();
        if (company.getType() == BusinessType.PANADERIA) {
            throw new BusinessException("El servicio de mesas solo esta disponible para bares y restaurantes");
        }
        TableSession session = new TableSession();
        session.setCompany(company);
        session.setWaiter(userRepository.findByEmailIgnoreCase(currentUser.email()).orElse(null));
        session.setGuests(request.guests());
        session.setNotes(blankToNull(request.notes()));
        session.occupy(freeTables(request.tableIds(), -1L));
        sessionRepository.save(session);
        changed(session);
        return ServiceDto.SessionResponse.from(session);
    }

    /**
     * Cambia comensales y notas. Si algun comensal ya ha pagado su parte, el numero de comensales no cambia
     * (las partes de los demas dejarian de cuadrar con lo que ya se cobro).
     */
    public ServiceDto.SessionResponse update(Long id, ServiceDto.UpdateRequest request) {
        TableSession session = getOpen(id);
        if (request.guests() != session.getGuests() && session.anyGuestHasPaid()) {
            throw new BusinessException("Ya ha pagado algun comensal su parte: no se puede cambiar el numero de comensales");
        }
        session.setGuests(request.guests());
        session.setNotes(blankToNull(request.notes()));
        changed(session);
        return ServiceDto.SessionResponse.from(session);
    }

    /** Juntar, separar o cambiar de mesa: el servicio pasa a ocupar exactamente estas mesas. */
    public ServiceDto.SessionResponse changeTables(Long id, ServiceDto.TablesRequest request) {
        TableSession session = getOpen(id);
        session.occupy(freeTables(request.tableIds(), session.getId()));
        changed(session, "kitchen");
        return ServiceDto.SessionResponse.from(session);
    }

    /** Envia una ronda: los productos pasan a la cuenta de la mesa con el numero de ronda. */
    public ServiceDto.SessionResponse addRound(Long id, ServiceDto.RoundRequest request) {
        TableSession session = getOpen(id);
        List<ServiceLine> round = new ArrayList<>();
        for (ServiceDto.LineRequest line : request.lines()) {
            Product product = productService.getEntity(line.productId());
            if (!product.isActive()) {
                throw new BusinessException("El producto " + product.getName() + " no esta disponible");
            }
            Set<Long> removed = line.removedIngredientIds() == null ? Set.of() : new TreeSet<>(line.removedIngredientIds());
            round.add(new ServiceLine(product, line.quantity(), blankToNull(line.notes()),
                    OrderService.removedNames(product, removed), productService.extras(product, line.addedIngredientIds())));
        }
        session.addRound(round);
        sessionRepository.flush(); // fechas e ids de las lineas nuevas en la respuesta
        changed(session, "kitchen");
        return ServiceDto.SessionResponse.from(session);
    }

    /**
     * Quita un producto de la cuenta (pedido por error). Solo si cocina aun no lo ha empezado;
     * lo que no se prepara (estacion NINGUNA) se puede quitar mientras no se haya servido.
     */
    public ServiceDto.SessionResponse removeLine(Long id, Long lineId) {
        TableSession session = getOpen(id);
        ServiceLine line = session.getLines().stream()
                .filter(l -> l.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Linea", lineId));
        boolean cancellable = line.getStatus() == ItemStatus.PENDIENTE
                || (line.getStation() == Station.NINGUNA && line.getStatus() != ItemStatus.SERVIDO);
        if (!cancellable) {
            throw new BusinessException(line.getProduct().getName() + " ya se esta preparando o esta hecho: avisa en cocina");
        }
        if (session.getTotal().subtract(line.getSubtotal()).compareTo(session.getPaid()) < 0) {
            throw new BusinessException("Ya se ha cobrado mas de lo que quedaria: anula antes el cobro");
        }
        session.getLines().remove(line);
        changed(session, "kitchen");
        return ServiceDto.SessionResponse.from(session);
    }

    /** Libera la mesa (los clientes se han ido). Tiene que estar todo cobrado. */
    public ServiceDto.SessionResponse close(Long id) {
        TableSession session = getOpen(id);
        if (session.getPending().signum() > 0) {
            throw new BusinessException("Quedan " + euros(session.getPending()) + " por cobrar en la mesa " + session.getTableLabel());
        }
        session.close();
        changed(session, "kitchen");
        return ServiceDto.SessionResponse.from(session);
    }

    // ----- Cobro -----

    @Transactional(readOnly = true)
    public ServiceDto.BillResponse bill(Long id) {
        return ServiceDto.BillResponse.from(getEntity(id));
    }

    /**
     * Cobra una parte o el total. Reglas: no mas de lo pendiente; un comensal paga exactamente su parte
     * y una sola vez; en efectivo, lo entregado cubre el importe. Si queda pagada y se pide, se libera la mesa.
     */
    public ServiceDto.SessionResponse pay(Long id, ServiceDto.PaymentRequest request) {
        TableSession session = getOpen(id);
        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(session.getPending()) > 0) {
            throw new BusinessException("Solo quedan " + euros(session.getPending()) + " por cobrar");
        }
        String label = request.label() == null || request.label().isBlank() ? "Pago" : request.label().trim();
        if (request.guest() != null) {
            int guest = request.guest();
            BigDecimal share = session.guestShares().get(guest);
            if (share == null) {
                throw new BusinessException("La mesa tiene " + session.getGuests() + " comensales: no existe el comensal " + guest);
            }
            if (session.guestHasPaid(guest)) {
                throw new BusinessException("El comensal " + guest + " ya ha pagado");
            }
            if (share.compareTo(amount) != 0) {
                throw new BusinessException("Al comensal " + guest + " le tocan " + euros(share));
            }
            label = "Comensal " + guest;
        }
        Payment payment = new Payment();
        payment.setSession(session);
        payment.setAmount(amount);
        payment.setMethod(request.method());
        payment.setGuest(request.guest());
        payment.setLabel(label);
        if (request.method() == PaymentMethod.EFECTIVO && request.cashGiven() != null) {
            if (request.cashGiven().compareTo(amount) < 0) {
                throw new BusinessException("Lo entregado (" + euros(request.cashGiven()) + ") no llega a " + euros(amount));
            }
            payment.setCashGiven(request.cashGiven().setScale(2, RoundingMode.HALF_UP));
        }
        payment.setCashier(userRepository.findByEmailIgnoreCase(currentUser.email()).orElse(null));
        session.getPayments().add(payment);
        paymentRepository.save(payment);
        if (request.closeWhenPaid() && session.getPending().signum() == 0) {
            session.close();
        }
        changed(session);
        return ServiceDto.SessionResponse.from(session);
    }

    /** Anula un cobro hecho por error (solo con la mesa abierta). */
    public ServiceDto.SessionResponse removePayment(Long id, Long paymentId) {
        TableSession session = getOpen(id);
        Payment payment = session.getPayments().stream()
                .filter(p -> p.getId().equals(paymentId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Cobro", paymentId));
        session.getPayments().remove(payment);
        paymentRepository.delete(payment);
        changed(session);
        return ServiceDto.SessionResponse.from(session);
    }

    /** Caja de un dia: cobrado por forma de pago y mesas cerradas. */
    @Transactional(readOnly = true)
    public ServiceDto.DayResponse day(LocalDate date) {
        Long companyId = currentUser.companyId();
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = from.plusDays(1);
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal card = BigDecimal.ZERO;
        for (Object[] row : paymentRepository.totalsByMethod(companyId, from, to)) {
            if (row[0] == PaymentMethod.EFECTIVO) cash = (BigDecimal) row[1];
            else card = (BigDecimal) row[1];
        }
        return ServiceDto.DayResponse.of(date, cash, card, sessionRepository.findClosedBetween(companyId, from, to));
    }

    private static String euros(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + " €";
    }

    /** Avisa a las pantallas de la empresa: la sala, la comanda de esta mesa y, si toca, la cocina. */
    private void changed(TableSession session, String... more) {
        List<String> scopes = new ArrayList<>(List.of("floor", "session:" + session.getId()));
        scopes.addAll(List.of(more));
        events.publishEvent(LiveEvent.change(session.getCompany().getId(), scopes.toArray(String[]::new)));
    }

    /** Mesas de la empresa sin otro servicio abierto (excludeId = el propio servicio, o -1). */
    private Set<DiningTable> freeTables(List<Long> ids, Long excludeId) {
        Set<DiningTable> tables = new LinkedHashSet<>();
        for (Long tableId : new LinkedHashSet<>(ids)) {
            DiningTable table = diningService.getTable(tableId);
            sessionRepository.findOpenWithTable(tableId, excludeId).ifPresent(other -> {
                throw new BusinessException("La mesa " + table.getName() + " ya esta ocupada");
            });
            tables.add(table);
        }
        return tables;
    }

    private TableSession getOpen(Long id) {
        TableSession session = getEntity(id);
        if (!session.isOpen()) {
            throw new BusinessException("La mesa " + session.getTableLabel() + " ya esta cerrada");
        }
        return session;
    }

    TableSession getEntity(Long id) {
        return sessionRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> new NotFoundException("Servicio de mesa", id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
