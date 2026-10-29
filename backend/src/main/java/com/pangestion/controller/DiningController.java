package com.pangestion.controller;

import com.pangestion.dto.DiningDto;
import com.pangestion.dto.SortRequest;
import com.pangestion.service.DiningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Plano de la sala de un bar o restaurante: zonas (/areas) y sus mesas (/tables). */
@RestController
@RequestMapping("/api/dining")
@RequiredArgsConstructor
public class DiningController {

    private final DiningService diningService;

    /** Todas las zonas con sus mesas, en el orden de las pestanas. */
    @GetMapping("/areas")
    public List<DiningDto.AreaResponse> findAreas() {
        return diningService.findAreas();
    }

    @GetMapping("/areas/{id}")
    public DiningDto.AreaResponse findArea(@PathVariable Long id) {
        return diningService.findArea(id);
    }

    /** 201 con la cabecera Location de la zona creada. */
    @PostMapping("/areas")
    public ResponseEntity<DiningDto.AreaResponse> createArea(@Valid @RequestBody DiningDto.AreaRequest request) {
        DiningDto.AreaResponse created = diningService.createArea(request);
        return ResponseEntity.created(Locations.child(created.id())).body(created);
    }

    @PutMapping("/areas/{id}")
    public DiningDto.AreaResponse updateArea(@PathVariable Long id, @Valid @RequestBody DiningDto.AreaRequest request) {
        return diningService.updateArea(id, request);
    }

    /** Body: {"ids": [3, 1, 2]} con las zonas en el orden deseado. */
    @PutMapping("/areas/order")
    public List<DiningDto.AreaResponse> reorderAreas(@Valid @RequestBody SortRequest request) {
        return diningService.reorderAreas(request.ids());
    }

    /** Borra la zona y sus mesas. */
    @DeleteMapping("/areas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteArea(@PathVariable Long id) {
        diningService.deleteArea(id);
    }

    /** Creacion anidada: la mesa queda en la zona de la ruta. 201 con Location a /api/dining/tables/{id}. */
    @PostMapping("/areas/{areaId}/tables")
    public ResponseEntity<DiningDto.TableResponse> createTable(@PathVariable Long areaId,
                                                               @Valid @RequestBody DiningDto.TableRequest request) {
        DiningDto.TableResponse created = diningService.createTable(areaId, request);
        return ResponseEntity.created(Locations.of("/api/dining/tables/{id}", created.id())).body(created);
    }

    @PutMapping("/tables/{id}")
    public DiningDto.TableResponse updateTable(@PathVariable Long id, @Valid @RequestBody DiningDto.TableRequest request) {
        return diningService.updateTable(id, request);
    }

    @DeleteMapping("/tables/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTable(@PathVariable Long id) {
        diningService.deleteTable(id);
    }
}
