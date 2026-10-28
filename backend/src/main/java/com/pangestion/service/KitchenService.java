package com.pangestion.service;

import com.pangestion.model.OrderStatus;
import com.pangestion.dto.KitchenDto;
import com.pangestion.events.LiveEvent;
import com.pangestion.exception.BusinessException;
import com.pangestion.exception.NotFoundException;
import com.pangestion.model.ItemStatus;
import com.pangestion.model.Order;
import com.pangestion.model.OrderLine;
import com.pangestion.model.ServiceLine;
import com.pangestion.model.Station;
import com.pangestion.model.TableSession;
import com.pangestion.repository.OrderLineRepository;
import com.pangestion.repository.ServiceLineRepository;
import com.pangestion.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Pantalla de cocina y barra. Junta en tickets los productos de una estacion: cada ronda de una mesa
 * y cada pedido para llevar de hoy. Cada producto cambia de estado por separado.
 * Reglas: solo se avanza o se retrocede un paso; un pedido para llevar no se "sirve" (se entrega desde Pedidos)
 * y su estado sigue al de sus productos.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class KitchenService {

    private final ServiceLineRepository serviceLineRepository;
    private final OrderLineRepository orderLineRepository;
    private final CurrentUser currentUser;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<KitchenDto.Ticket> tickets(Station station) {
        Long companyId = currentUser.companyId();
        List<KitchenDto.Ticket> tickets = new ArrayList<>();

        // Mesas: un ticket por ronda de cada servicio
        Map<String, List<ServiceLine>> rounds = new LinkedHashMap<>();
        for (ServiceLine line : serviceLineRepository.findForKitchen(companyId, station)) {
            rounds.computeIfAbsent(line.getSession().getId() + "/" + line.getRound(), k -> new ArrayList<>()).add(line);
        }
        for (List<ServiceLine> lines : rounds.values()) {
            lines.sort(Comparator.comparing(ServiceLine::getId));
            TableSession session = lines.getFirst().getSession();
            LocalDateTime time = lines.stream().map(ServiceLine::getCreatedAt).min(Comparator.naturalOrder()).orElseThrow();
            String detail = "Ronda " + lines.getFirst().getRound() + " · " + session.getGuests() + " pers"
                    + (session.getWaiter() == null ? "" : " · " + session.getWaiter().getName());
            tickets.add(new KitchenDto.Ticket(KitchenDto.Source.MESA, session.getId(), lines.getFirst().getRound(),
                    "Mesa " + session.getTableLabel(), detail, session.getNotes(), time,
                    byKitchenOrder(lines.stream().map(KitchenDto.Item::from))));
        }

        // Para llevar: un ticket por pedido de hoy
        Map<Long, List<OrderLine>> orders = new LinkedHashMap<>();
        for (OrderLine line : orderLineRepository.findForKitchen(companyId, LocalDate.now(), station)) {
            orders.computeIfAbsent(line.getOrder().getId(), k -> new ArrayList<>()).add(line);
        }
        for (List<OrderLine> lines : orders.values()) {
            lines.sort(Comparator.comparing(OrderLine::getId));
            Order order = lines.getFirst().getOrder();
            tickets.add(new KitchenDto.Ticket(KitchenDto.Source.LLEVAR, order.getId(), null,
                    "#" + order.getId(), order.getCustomer().getName()
                            + (order.getType() == com.pangestion.model.OrderType.DOMICILIO ? " · domicilio " : " · recoge ") + order.getPickupTime(),
                    order.getNotes(), order.getPickupDate().atTime(order.getPickupTime()),
                    byKitchenOrder(lines.stream().map(KitchenDto.Item::from))));
        }

        tickets.sort(Comparator.comparing(KitchenDto.Ticket::time));
        return tickets;
    }

    /** Productos de un ticket en el orden de cocina de su categoria (entrantes antes que principales); dentro, como se pidieron. */
    private static List<KitchenDto.Item> byKitchenOrder(java.util.stream.Stream<KitchenDto.Item> items) {
        return items.sorted(Comparator.comparingInt(KitchenDto.Item::categoryOrder)).toList();
    }

    /** Pasa los productos indicados al estado pedido. Si alguno no puede, no se cambia ninguno. */
    public void update(KitchenDto.UpdateRequest request) {
        Long companyId = currentUser.companyId();
        ItemStatus target = request.status();
        Set<Order> touchedOrders = new LinkedHashSet<>();
        Set<String> scopes = new LinkedHashSet<>(List.of("kitchen"));
        // Productos de mesa que acaban de quedar listos, por servicio (para avisar al camarero)
        Map<TableSession, List<String>> ready = new LinkedHashMap<>();
        for (KitchenDto.ItemRef ref : request.items()) {
            if (ref.source() == KitchenDto.Source.MESA) {
                ServiceLine line = serviceLineRepository.findByIdAndSessionCompanyId(ref.id(), companyId)
                        .orElseThrow(() -> new NotFoundException("Producto de mesa", ref.id()));
                if (!line.getSession().isOpen()) {
                    throw new BusinessException("La mesa " + line.getSession().getTableLabel() + " ya esta cerrada");
                }
                if (line.getStatus() != target) {
                    check(line.getProduct().getName(), line.getStatus(), target, line.getStation());
                    int units = units(ref, line.getProduct().getName(), line.getQuantity());
                    if (target == ItemStatus.LISTO) {
                        ready.computeIfAbsent(line.getSession(), s -> new ArrayList<>()).add(describe(line, units));
                    }
                    scopes.add("floor");
                    scopes.add("session:" + line.getSession().getId());
                    move(line, units, target);
                }
            } else {
                OrderLine line = orderLineRepository.findByIdAndOrderCompanyId(ref.id(), companyId)
                        .orElseThrow(() -> new NotFoundException("Producto de pedido", ref.id()));
                if (target == ItemStatus.SERVIDO) {
                    throw new BusinessException("Los pedidos para llevar se entregan desde Pedidos, no se sirven en mesa");
                }
                if (line.getStatus() != target) {
                    check(line.getProduct().getName(), line.getStatus(), target, line.getStation());
                    touchedOrders.add(line.getOrder());
                    scopes.add("orders");
                    move(line, units(ref, line.getProduct().getName(), line.getQuantity()), target);
                }
            }
        }
        // Estado de cada pedido antes de seguir a sus productos: si uno a domicilio queda listo, se avisa a los repartidores
        Map<Order, OrderStatus> before = new LinkedHashMap<>();
        touchedOrders.forEach(o -> before.put(o, o.getStatus()));
        touchedOrders.forEach(Order::syncStatusFromLines);
        if (!touchedOrders.isEmpty()) {
            scopes.add("delivery");
        }

        events.publishEvent(LiveEvent.change(companyId, scopes.toArray(String[]::new)));
        before.forEach((order, status) -> LiveEvent.deliveryReady(order, status).ifPresent(events::publishEvent));
        ready.forEach((session, items) -> events.publishEvent(LiveEvent.ready(companyId, new LiveEvent.ReadyData(
                session.getId(), session.getTableLabel(),
                session.getWaiter() == null ? null : session.getWaiter().getName(), items))));
    }

    /** Unidades a mover: las pedidas (como mucho las que tiene la linea) o, si no se indica, todas. */
    private static int units(KitchenDto.ItemRef ref, String product, int quantity) {
        if (ref.units() == null) {
            return quantity;
        }
        if (ref.units() > quantity) {
            throw new BusinessException("Solo quedan " + quantity + " de " + product);
        }
        return ref.units();
    }

    /**
     * Mueve unas unidades de una linea de mesa. Si no son todas, se separan en otra linea.
     * Si en ese estado ya hay lo mismo de la misma ronda, se juntan (3× Calzone listas, no 1× + 1× + 1×).
     */
    private static void move(ServiceLine line, int units, ItemStatus target) {
        List<ServiceLine> lines = line.getSession().getLines();
        ServiceLine moving = units == line.getQuantity() ? line : line.split(units);
        moving.moveTo(target);
        Optional<ServiceLine> twin = lines.stream()
                .filter(l -> l != moving && l.getStatus() == target && l.sameDishAs(moving))
                .findFirst();
        if (twin.isPresent()) {
            twin.get().setQuantity(twin.get().getQuantity() + units);
            twin.get().setStatusChangedAt(moving.getStatusChangedAt());
            if (moving == line) {
                lines.remove(line);
            }
        } else if (moving != line) {
            lines.add(moving);
        }
    }

    /** Lo mismo para un pedido para llevar. */
    private static void move(OrderLine line, int units, ItemStatus target) {
        List<OrderLine> lines = line.getOrder().getLines();
        OrderLine moving = units == line.getQuantity() ? line : line.split(units);
        moving.moveTo(target);
        Optional<OrderLine> twin = lines.stream()
                .filter(l -> l != moving && l.getStatus() == target && l.sameDishAs(moving))
                .findFirst();
        if (twin.isPresent()) {
            twin.get().setQuantity(twin.get().getQuantity() + units);
            twin.get().setStatusChangedAt(moving.getStatusChangedAt());
            if (moving == line) {
                lines.remove(line);
            }
        } else if (moving != line) {
            lines.add(moving);
        }
    }

    /** "2× Hamburguesa" para el aviso del camarero. */
    private static String describe(ServiceLine line, int units) {
        return units + "× " + line.getProduct().getName();
    }

    private static void check(String product, ItemStatus current, ItemStatus target, Station station) {
        if (!current.canMoveTo(target, station)) {
            throw new BusinessException(product + " esta " + label(current) + ": no puede pasar a " + label(target));
        }
    }

    private static String label(ItemStatus status) {
        return switch (status) {
            case PENDIENTE -> "pendiente";
            case PREPARANDO -> "preparandose";
            case LISTO -> "listo";
            case SERVIDO -> "servido";
        };
    }
}
