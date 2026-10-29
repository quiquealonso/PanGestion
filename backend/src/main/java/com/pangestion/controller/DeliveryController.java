package com.pangestion.controller;

import com.pangestion.dto.DeliveryDto;
import com.pangestion.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Reparto a domicilio: /api/delivery. ADMIN, GESTOR y REPARTIDOR. */
@RestController
@RequestMapping("/api/delivery")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    /** Pedidos a domicilio de hoy, por hora de entrega. */
    @GetMapping
    public List<DeliveryDto.Item> today() {
        return deliveryService.today();
    }

    /** Me lo llevo: LISTO → TRANSPORTANDO, a mi nombre. */
    @PostMapping("/{id}/take")
    public DeliveryDto.Item take(@PathVariable Long id) {
        return deliveryService.take(id);
    }

    /** Entregado: TRANSPORTANDO → ENTREGADO. */
    @PostMapping("/{id}/deliver")
    public DeliveryDto.Item deliver(@PathVariable Long id) {
        return deliveryService.deliver(id);
    }

    /** No me lo llevo: TRANSPORTANDO → LISTO, sin repartidor. */
    @PostMapping("/{id}/release")
    public DeliveryDto.Item release(@PathVariable Long id) {
        return deliveryService.release(id);
    }
}
