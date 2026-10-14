package com.pangestion.service;

import com.pangestion.dto.OrderDto;
import com.pangestion.exception.BusinessException;
import com.pangestion.exception.NotFoundException;
import com.pangestion.model.*;
import com.pangestion.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerService customerService;
    private final StoreService storeService;
    private final ProductService productService;

    @Transactional(readOnly = true)
    public List<OrderDto.Response> search(String search, OrderStatus status, Long storeId, LocalDate from, LocalDate to) {
        return orderRepository.search(SearchUtils.likePattern(search), status, storeId, from, to).stream()
                .map(OrderDto.Response::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderDto.Response> findByCustomer(Long customerId) {
        customerService.getEntity(customerId);
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
        Store store = storeService.getEntity(request.storeId());
        storeService.validateSlot(store, request.pickupDate(), request.pickupTime(), -1L, true);

        Order order = new Order();
        order.setCustomer(customerService.getEntity(request.customerId()));
        fill(order, store, request.pickupDate(), request.pickupTime(), request.notes());
        setLines(order, request.lines(), Set.of());
        return OrderDto.Response.from(orderRepository.save(order));
    }

    /** Edicion desde el panel. Si cambia la franja se vuelve a comprobar la disponibilidad. */
    public OrderDto.Response update(Long id, OrderDto.Request request) {
        Order order = getEntity(id);
        if (order.getStatus() == OrderStatus.ENTREGADO || order.getStatus() == OrderStatus.CANCELADO) {
            throw new BusinessException("No se puede editar un pedido entregado o cancelado");
        }

        Store store = storeService.getEntity(request.storeId());
        boolean slotChanged = !store.getId().equals(order.getStore().getId())
                || !request.pickupDate().equals(order.getPickupDate())
                || !request.pickupTime().equals(order.getPickupTime());
        if (slotChanged) {
            storeService.validateSlot(store, request.pickupDate(), request.pickupTime(), order.getId(), true);
        }

        // Los productos que ya estaban en el pedido se permiten aunque se hayan desactivado despues
        Set<Long> previousProducts = order.getLines().stream()
                .map(line -> line.getProduct().getId())
                .collect(Collectors.toSet());

        order.setCustomer(customerService.getEntity(request.customerId()));
        fill(order, store, request.pickupDate(), request.pickupTime(), request.notes());
        setLines(order, request.lines(), previousProducts);
        return OrderDto.Response.from(order);
    }

    public OrderDto.Response changeStatus(Long id, OrderStatus status) {
        Order order = getEntity(id);
        order.setStatus(status);
        return OrderDto.Response.from(order);
    }

    public void delete(Long id) {
        orderRepository.delete(getEntity(id));
    }

    /** Pedido hecho por el cliente desde la parte publica: el cliente se identifica por su email. */
    public OrderDto.Response createPublic(OrderDto.PublicRequest request) {
        Store store = storeService.getEntity(request.storeId());
        storeService.validateSlot(store, request.pickupDate(), request.pickupTime(), -1L, true);

        Order order = new Order();
        order.setCustomer(customerService.findOrCreate(request.customer()));
        fill(order, store, request.pickupDate(), request.pickupTime(), request.notes());
        setLines(order, request.lines(), Set.of());
        return OrderDto.Response.from(orderRepository.save(order));
    }

    private void fill(Order order, Store store, LocalDate date, LocalTime time, String notes) {
        order.setStore(store);
        order.setPickupDate(date);
        order.setPickupTime(time);
        order.setNotes(notes);
    }

    /** Sustituye las lineas. Si un producto viene repetido se suman las cantidades. */
    private void setLines(Order order, List<OrderDto.Line> lines, Set<Long> allowedInactive) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        lines.forEach(line -> quantities.merge(line.productId(), line.quantity(), Integer::sum));

        order.clearLines();
        quantities.forEach((productId, quantity) -> {
            Product product = productService.getEntity(productId);
            if (!product.isActive() && !allowedInactive.contains(productId)) {
                throw new BusinessException("El producto " + product.getName() + " no esta disponible");
            }
            order.addLine(product, quantity);
        });
    }

    Order getEntity(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new NotFoundException("Pedido", id));
    }
}
