package com.pangestion.controller;

import com.pangestion.dto.KitchenDto;
import com.pangestion.model.Station;
import com.pangestion.service.KitchenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Pantalla de cocina y barra: /api/kitchen. ADMIN, GESTOR, CAMARERO y COCINA. */
@RestController
@RequestMapping("/api/kitchen")
@RequiredArgsConstructor
public class KitchenController {

    private final KitchenService kitchenService;

    /** Tickets de una estacion (?station=COCINA o BARRA), del mas antiguo al mas nuevo. */
    @GetMapping
    public List<KitchenDto.Ticket> tickets(@RequestParam(defaultValue = "COCINA") Station station) {
        return kitchenService.tickets(station);
    }

    /** Cambia el estado de uno o varios productos. Body: {"items": [{"source": "MESA", "id": 7}], "status": "LISTO"}. */
    @PatchMapping("/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@Valid @RequestBody KitchenDto.UpdateRequest request) {
        kitchenService.update(request);
    }
}
