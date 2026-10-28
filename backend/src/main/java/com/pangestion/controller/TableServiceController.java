package com.pangestion.controller;

import com.pangestion.dto.ServiceDto;
import com.pangestion.service.TableSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Servicio de mesas (camareros): /api/service. Pueden usarlo ADMIN, GESTOR y CAMARERO. */
@RestController
@RequestMapping("/api/service")
@RequiredArgsConstructor
public class TableServiceController {

    private final TableSessionService sessionService;

    /** Plano en vivo: zonas, mesas y el servicio abierto de cada mesa ocupada. */
    @GetMapping("/floor")
    public List<ServiceDto.FloorArea> floor() {
        return sessionService.floor();
    }

    @GetMapping("/sessions/{id}")
    public ServiceDto.SessionResponse find(@PathVariable Long id) {
        return sessionService.find(id);
    }

    /** Abrir mesa. 201 con Location del servicio. */
    @PostMapping("/sessions")
    public ResponseEntity<ServiceDto.SessionResponse> open(@Valid @RequestBody ServiceDto.OpenRequest request) {
        ServiceDto.SessionResponse created = sessionService.open(request);
        return ResponseEntity.created(Locations.child(created.id())).body(created);
    }

    @PutMapping("/sessions/{id}")
    public ServiceDto.SessionResponse update(@PathVariable Long id, @Valid @RequestBody ServiceDto.UpdateRequest request) {
        return sessionService.update(id, request);
    }

    /** Juntar, separar o cambiar de mesa. Body: {"tableIds": [4, 5]}. */
    @PutMapping("/sessions/{id}/tables")
    public ServiceDto.SessionResponse changeTables(@PathVariable Long id, @Valid @RequestBody ServiceDto.TablesRequest request) {
        return sessionService.changeTables(id, request);
    }

    /** Enviar una ronda de productos. */
    @PostMapping("/sessions/{id}/rounds")
    public ServiceDto.SessionResponse addRound(@PathVariable Long id, @Valid @RequestBody ServiceDto.RoundRequest request) {
        return sessionService.addRound(id, request);
    }

    @DeleteMapping("/sessions/{id}/lines/{lineId}")
    public ServiceDto.SessionResponse removeLine(@PathVariable Long id, @PathVariable Long lineId) {
        return sessionService.removeLine(id, lineId);
    }

    /** Cuenta para cobrar: total, cobrado, pendiente y la parte de cada comensal. */
    @GetMapping("/sessions/{id}/bill")
    public ServiceDto.BillResponse bill(@PathVariable Long id) {
        return sessionService.bill(id);
    }

    /** Cobrar el total o una parte (por comensal o a partes iguales). */
    @PostMapping("/sessions/{id}/payments")
    public ServiceDto.SessionResponse pay(@PathVariable Long id, @Valid @RequestBody ServiceDto.PaymentRequest request) {
        return sessionService.pay(id, request);
    }

    /** Anular un cobro hecho por error. */
    @DeleteMapping("/sessions/{id}/payments/{paymentId}")
    public ServiceDto.SessionResponse removePayment(@PathVariable Long id, @PathVariable Long paymentId) {
        return sessionService.removePayment(id, paymentId);
    }

    /** Caja de un dia (?date=2026-10-27; por defecto hoy): cobrado en efectivo y tarjeta, y mesas cerradas. */
    @GetMapping("/day")
    public ServiceDto.DayResponse day(@RequestParam(required = false) LocalDate date) {
        return sessionService.day(date == null ? LocalDate.now() : date);
    }

    /** Liberar la mesa (todo cobrado). */
    @PostMapping("/sessions/{id}/close")
    public ServiceDto.SessionResponse close(@PathVariable Long id) {
        return sessionService.close(id);
    }
}
