package com.pangestion.controller;

import com.pangestion.dto.DiningReportDto;
import com.pangestion.service.DiningReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Informes: /api/reports. ADMIN, GESTOR y VISOR (los camareros y la cocina no ven la facturacion). */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final DiningReportService diningReportService;

    /** Informe de sala entre dos fechas incluidas (?from=2026-10-01&to=2026-10-27; por defecto, los ultimos 7 dias). */
    @GetMapping("/dining")
    public DiningReportDto dining(@RequestParam(required = false) LocalDate from,
                                  @RequestParam(required = false) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        return diningReportService.report(from == null ? end.minusDays(6) : from, end);
    }
}
