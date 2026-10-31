package com.pangestion.service;

import com.pangestion.model.CustomerAccount;
import com.pangestion.model.OrderType;
import com.pangestion.events.LiveEvent;
import com.pangestion.dto.OrderDto;
import com.pangestion.exception.BusinessException;
import com.pangestion.exception.NotFoundException;
import com.pangestion.model.*;
import com.pangestion.repository.OrderRepository;
import com.pangestion.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Pedidos de la empresa del usuario logueado, y pedidos que llegan desde la web publica. */
@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final com.pangestion.repository.OrderLineRepository orderLineRepository;
    private final CustomerService customerService;
    private final CompanyService companyService;
    private final ProductService productService;
    private final CurrentUser currentUser;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<OrderDto.Response> search(String search, OrderStatus status, LocalDate from, LocalDate to) {
        return orderRepository.search(currentUser.companyId(), SearchUtils.likePattern(search), status, from, to).stream()
                .map(OrderDto.Response::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderDto.Response> findByCustomer(Long customerId) {
        customerService.getEntity(customerId); // comprueba que el cliente es de esta empresa
        return orderRepository.findByCustomerIdOrderByPickupDateDesc(customerId).stream()
                .map(OrderDto.Response::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDto.Response findById(Long id) {
        return OrderDto.Response.from(getEntity(id));
    }

    /** Alta desde el panel de gestion. */
    public OrderDto.Response create(OrderDto.Request request) {
        Company company = companyService.current();
        companyService.validateSlot(company, request.pickupDate(), request.pickupTime(), -1L);

        Order order = new Order();
        order.setCompany(company);
        order.setCustomer(customerService.getEntity(request.customerId()));
        order.setPickupDate(request.pickupDate());
        order.setPickupTime(request.pickupTime());
        order.setNotes(request.notes());
        setDelivery(order, request.type(), request.deliveryAddress());
        setLines(order, request.lines(), Set.of());
        Order saved = orderRepository.save(order);
        changed(saved, null);
        return OrderDto.Response.from(saved);
    }

    /** Alta anidada bajo un cliente: primero se comprueba que el cliente es de esta empresa (404 si no). */
    public OrderDto.Response createForCustomer(Long customerId, OrderDto.CustomerOrderRequest request) {
        customerService.getEntity(customerId);
        return create(new OrderDto.Request(customerId, request.pickupDate(), request.pickupTime(),
                request.notes(), request.lines(), request.type(), request.deliveryAddress()));
    }

    /** Edicion desde el panel. Si cambia la franja se vuelve a comprobar la disponibilidad. */
    public OrderDto.Response update(Long id, OrderDto.Request request) {
        Order order = getEntity(id);
        if (order.getStatus() == OrderStatus.ENTREGADO || order.getStatus() == OrderStatus.CANCELADO) {
            throw new BusinessException("No se puede editar un pedido entregado o cancelado");
        }
        boolean slotChanged = !request.pickupDate().equals(order.getPickupDate())
                || !request.pickupTime().equals(order.getPickupTime());
        if (slotChanged) {
            companyService.validateSlot(order.getCompany(), request.pickupDate(), request.pickupTime(), order.getId());
        }

        // Los productos que ya estaban en el pedido se permiten aunque se hayan desactivado despues
        Set<Long> previousProducts = order.getLines().stream()
                .map(line -> line.getProduct().getId())
                .collect(Collectors.toSet());

        order.setCustomer(customerService.getEntity(request.customerId()));
        order.setPickupDate(request.pickupDate());
        order.setPickupTime(request.pickupTime());
        order.setNotes(request.notes());
        setDelivery(order, request.type(), request.deliveryAddress());
        setLines(order, request.lines(), previousProducts);
        changed(order, order.getStatus());
        return OrderDto.Response.from(order);
    }

    /**
     * Cambio de estado desde el panel. TRANSPORTANDO solo para pedidos a domicilio; al salir de TRANSPORTANDO hacia atras
     * se quita el repartidor. Si un pedido a domicilio queda LISTO, se avisa a los repartidores.
     */
    public OrderDto.Response changeStatus(Long id, OrderStatus status) {
        Order order = getEntity(id);
        OrderStatus before = order.getStatus();
        if (status == OrderStatus.TRANSPORTANDO && order.getType() != OrderType.DOMICILIO) {
            throw new BusinessException("Solo los pedidos a domicilio se transportan");
        }
        if (status == OrderStatus.TRANSPORTANDO && before != OrderStatus.TRANSPORTANDO) {
            order.setDispatchedAt(LocalDateTime.now());
        } else if (status.compareTo(OrderStatus.TRANSPORTANDO) < 0) {
            order.setDriver(null);
            order.setDispatchedAt(null);
        }
        order.setStatus(status);
        changed(order, before);
        return OrderDto.Response.from(order);
    }

    /** Recoger o a domicilio. A domicilio exige direccion y que el negocio haga envios. */
    private static void setDelivery(Order order, OrderType type, String address) {
        OrderType value = type == null ? OrderType.RECOGER : type;
        if (value == OrderType.DOMICILIO) {
            if (!order.getCompany().isDelivery()) {
                throw new BusinessException(order.getCompany().getName() + " no hace envios a domicilio");
            }
            if (address == null || address.isBlank()) {
                throw new BusinessException("Falta la direccion de entrega");
            }
            order.setDeliveryAddress(address.trim());
        } else {
            order.setDeliveryAddress(null);
        }
        order.setType(value);
    }

    /** Avisa a las pantallas (pedidos, cocina y reparto) y, si un pedido a domicilio acaba de quedar listo, a los repartidores. */
    private void changed(Order order, OrderStatus before) {
        events.publishEvent(LiveEvent.change(order.getCompany().getId(), "orders", "kitchen", "delivery"));
        LiveEvent.deliveryReady(order, before).ifPresent(events::publishEvent);
    }

    /**
     * Empieza la produccion de un dia: pasa a EN_PROCESO todos los pedidos PENDIENTES con recogida ese dia.
     * Los que ya estan en proceso, listos, entregados o cancelados no se tocan.
     * @return cuantos pedidos se han cambiado
     */
    public int startDay(LocalDate date) {
        List<Order> pending = orderRepository.findByCompanyIdAndPickupDateAndStatus(
                currentUser.companyId(), date, OrderStatus.PENDIENTE);
        pending.forEach(order -> order.setStatus(OrderStatus.EN_PROCESO));
        return pending.size();
    }

    /**
     * Lo que hay que preparar un dia: unidades por producto, personalizacion y hora de recogida.
     * Nunca cuenta los cancelados; con onlyPending tampoco los ya entregados (lo que queda por entregar).
     */
    @Transactional(readOnly = true)
    public List<OrderDto.ProductionRow> production(LocalDate date, boolean onlyPending) {
        var excluded = onlyPending
                ? List.of(OrderStatus.CANCELADO, OrderStatus.ENTREGADO)
                : List.of(OrderStatus.CANCELADO);
        Map<String, OrderDto.ProductionRow> rows = new LinkedHashMap<>();
        orderLineRepository.findForProduction(currentUser.companyId(), date, excluded).stream()
                .sorted(java.util.Comparator
                        .comparing((OrderLine l) -> l.getProduct().getCategory().getSortOrder())
                        .thenComparing(l -> l.getProduct().getCategory().getName())
                        .thenComparing(l -> l.getProduct().getSortOrder())
                        .thenComparing(l -> l.getProduct().getName())
                        .thenComparing(l -> String.join(",", l.getRemovedIngredients()))
                        .thenComparing(l -> String.join(",", LineExtra.names(l.getExtras())))
                        .thenComparing(l -> l.getOrder().getPickupTime()))
                .forEach(line -> {
                    Product product = line.getProduct();
                    List<String> extras = LineExtra.names(line.getExtras());
                    String key = product.getId() + "|" + line.getRemovedIngredients() + "|" + extras + "|" + line.getOrder().getPickupTime();
                    rows.merge(key,
                            new OrderDto.ProductionRow(product.getId(), product.getName(), product.getAbbreviation(),
                                    product.getCategory().getName(), product.getCategory().getColor(),
                                    List.copyOf(line.getRemovedIngredients()), extras, line.getOrder().getPickupTime(), line.getQuantity()),
                            (a, b) -> new OrderDto.ProductionRow(a.productId(), a.productName(), a.abbreviation(),
                                    a.categoryName(), a.categoryColor(), a.removedIngredients(), a.extras(), a.pickupTime(),
                                    a.quantity() + b.quantity()));
                });
        return List.copyOf(rows.values());
    }

    public void delete(Long id) {
        orderRepository.delete(getEntity(id));
    }

    /** Pedido hecho por un cliente desde la web publica a la panaderia que ha elegido. */
    public OrderDto.PublicResponse createPublic(OrderDto.PublicRequest request, CustomerAccount account) {
        Company company = companyService.getEntity(request.companyId());
        companyService.validateSlot(company, request.pickupDate(), request.pickupTime(), -1L);

        Order order = new Order();
        order.setCompany(company);
        String phone = request.phone() != null && !request.phone().isBlank() ? request.phone().trim() : account.getPhone();
        if (request.type() == OrderType.DOMICILIO && phone == null) {
            throw new BusinessException("Para llevartelo a casa necesitamos un telefono");
        }
        order.setCustomer(customerService.findOrCreate(company, account.getName(), account.getEmail(), phone));
        order.setPickupDate(request.pickupDate());
        order.setPickupTime(request.pickupTime());
        order.setNotes(request.notes());
        setDelivery(order, request.type(), request.deliveryAddress());
        setLines(order, request.lines(), Set.of());
        Order saved = orderRepository.save(order);
        changed(saved, null);
        return OrderDto.PublicResponse.from(saved, account.getName());
    }

    /**
     * Sustituye las lineas. Las lineas del mismo producto con la misma personalizacion se suman;
     * con distinta personalizacion ("pizza", "pizza sin cebolla" y "pizza con huevo") quedan separadas.
     * Solo se aceptan productos de la misma empresa que el pedido, solo se pueden quitar ingredientes que lleva
     * el producto y solo se anaden extras con precio de extra (ver ProductService.extras).
     */
    private void setLines(Order order, List<OrderDto.Line> lines, Set<Long> allowedInactive) {
        Map<String, LineDraft> drafts = new LinkedHashMap<>();
        for (OrderDto.Line line : lines) {
            Set<Long> removed = line.removedIngredientIds() == null ? Set.of() : new TreeSet<>(line.removedIngredientIds());
            Set<Long> added = line.addedIngredientIds() == null ? Set.of() : new TreeSet<>(line.addedIngredientIds());
            String key = line.productId() + ":" + removed + ":" + added;
            drafts.merge(key, new LineDraft(line.productId(), removed, added, line.quantity()),
                    (a, b) -> new LineDraft(a.productId(), a.removedIds(), a.addedIds(), a.quantity() + b.quantity()));
        }

        order.clearLines();
        for (LineDraft draft : drafts.values()) {
            Product product = productService.getEntity(draft.productId(), order.getCompany().getId());
            if (!product.isActive() && !allowedInactive.contains(draft.productId())) {
                throw new BusinessException("El producto " + product.getName() + " no esta disponible");
            }
            order.addLine(product, draft.quantity(), removedNames(product, draft.removedIds()),
                    productService.extras(product, draft.addedIds()));
        }
    }

    /** Nombres de los ingredientes a quitar, comprobando que el producto los lleva. Tambien lo usa el servicio de mesas. */
    static List<String> removedNames(Product product, Set<Long> removedIds) {
        List<String> names = new ArrayList<>();
        for (Long id : removedIds) {
            Ingredient ingredient = product.getIngredients().stream()
                    .filter(i -> i.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("El producto " + product.getName() + " no lleva ese ingrediente"));
            names.add(ingredient.getName());
        }
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    /** Linea pedida antes de convertirla en OrderLine. */
    private record LineDraft(Long productId, Set<Long> removedIds, Set<Long> addedIds, int quantity) {
    }

    Order getEntity(Long id) {
        return orderRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> new NotFoundException("Pedido", id));
    }
}
