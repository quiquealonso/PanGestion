package com.pangestion.controller;

import com.pangestion.dto.DashboardDto;
import com.pangestion.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public DashboardDto getSummary() {
        return dashboardService.getSummary();
    }

    /** Unidades vendidas por dia: /api/dashboard/weekly-sales?date=2026-10-18 (cualquier dia de la semana; por defecto hoy). */
    @GetMapping("/weekly-sales")
    public DashboardDto.WeeklySales weeklySales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate date) {
        return dashboardService.getWeeklySales(date != null ? date : java.time.LocalDate.now());
    }
}
