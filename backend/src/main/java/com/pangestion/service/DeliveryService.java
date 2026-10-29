package com.pangestion.service;

import com.pangestion.dto.DeliveryDto;
import com.pangestion.events.LiveEvent;
import com.pangestion.exception.BusinessException;
import com.pangestion.exception.NotFoundException;
import com.pangestion.model.Order;
import com.pangestion.model.OrderStatus;
import com.pangestion.model.OrderType;
import com.pangestion.model.Role;
import com.pangestion.model.User;
import com.pangestion.repository.OrderRepository;
import com.pangestion.repository.UserRepository;
import com.pangestion.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Reparto a domicilio: el repartidor ve los pedidos a domicilio de hoy, se lleva uno que este listo
 * (pasa a TRANSPORTANDO y queda a su nombre) y lo marca entregado al dejarlo.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DeliveryService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final ApplicationEventPublisher events;

    /** Pedidos a domicilio de hoy (sin los cancelados), por hora de entrega. */
    @Transactional(readOnly = true)
    public List<DeliveryDto.Item> today() {
        return orderRepository.findForDelivery(currentUser.companyId(), LocalDate.now()).stream()
                .map(o -> DeliveryDto.Item.from(o, currentUser.email()))
                .toList();
    }

    /** "Me lo llevo": un pedido listo pasa a TRANSPORTANDO a nombre de quien lo pide. */
    public DeliveryDto.Item take(Long id) {
        Order order = getDelivery(id);
        if (order.getStatus() == OrderStatus.TRANSPORTANDO) {
            String who = order.getDriver() == null ? "otro repartidor" : order.getDriver().getName();
            throw new BusinessException("El pedido #" + id + " ya lo lleva " + who);
        }
        if (order.getStatus() != OrderStatus.LISTO) {
            throw new BusinessException("El pedido #" + id + " no esta listo para llevar");
        }
        order.setStatus(OrderStatus.TRANSPORTANDO);
        order.setDriver(me());
        order.setDispatchedAt(LocalDateTime.now());
        changed(order);
        return DeliveryDto.Item.from(order, currentUser.email());
    }

    /** Entregado al cliente. Solo quien lo lleva (o un ADMIN o GESTOR). */
    public DeliveryDto.Item deliver(Long id) {
        Order order = getDelivery(id);
        if (order.getStatus() != OrderStatus.TRANSPORTANDO) {
            throw new BusinessException("El pedido #" + id + " no esta en reparto");
        }
        checkMine(order);
        order.setStatus(OrderStatus.ENTREGADO);
        changed(order);
        return DeliveryDto.Item.from(order, currentUser.email());
    }

    /** "No me lo llevo": vuelve a LISTO y sin repartidor, para que lo coja otro. */
    public DeliveryDto.Item release(Long id) {
        Order order = getDelivery(id);
        if (order.getStatus() != OrderStatus.TRANSPORTANDO) {
            throw new BusinessException("El pedido #" + id + " no esta en reparto");
        }
        checkMine(order);
        order.setStatus(OrderStatus.LISTO);
        order.setDriver(null);
        order.setDispatchedAt(null);
        changed(order);
        return DeliveryDto.Item.from(order, currentUser.email());
    }

    private void checkMine(Order order) {
        User me = me();
        boolean mine = order.getDriver() != null && order.getDriver().getId().equals(me.getId());
        if (!mine && me.getRole() == Role.REPARTIDOR) {
            throw new BusinessException("El pedido #" + order.getId() + " lo lleva " + order.getDriver().getName());
        }
    }

    private Order getDelivery(Long id) {
        Order order = orderRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> new NotFoundException("Pedido", id));
        if (order.getType() != OrderType.DOMICILIO) {
            throw new BusinessException("El pedido #" + id + " es para recoger, no se reparte");
        }
        return order;
    }

    private User me() {
        return userRepository.findByEmailIgnoreCase(currentUser.email()).orElseThrow();
    }

    private void changed(Order order) {
        events.publishEvent(LiveEvent.change(order.getCompany().getId(), "orders", "delivery"));
    }
}
