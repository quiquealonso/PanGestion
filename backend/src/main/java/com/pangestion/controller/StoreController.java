package com.pangestion.controller;

import com.pangestion.dto.StoreDto;
import com.pangestion.service.StoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    @GetMapping
    public List<StoreDto.Response> findAll() {
        return storeService.findAll(false);
    }

    @GetMapping("/{id}")
    public StoreDto.Response findById(@PathVariable Long id) {
        return storeService.findById(id);
    }

    /** Franjas de recogida de un dia: /api/stores/1/slots?date=2026-10-20 */
    @GetMapping("/{id}/slots")
    public List<StoreDto.Slot> getSlots(@PathVariable Long id,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return storeService.getSlots(id, date);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StoreDto.Response create(@Valid @RequestBody StoreDto.Request request) {
        return storeService.create(request);
    }

    @PutMapping("/{id}")
    public StoreDto.Response update(@PathVariable Long id, @Valid @RequestBody StoreDto.Request request) {
        return storeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        storeService.delete(id);
    }
}
