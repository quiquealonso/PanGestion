package com.pangestion.controller;

import com.pangestion.dto.CompanyDto;
import com.pangestion.security.CurrentUser;
import com.pangestion.service.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** La empresa del usuario logueado: sus datos, su horario y sus franjas de recogida. */
@RestController
@RequestMapping("/api/company")
@RequiredArgsConstructor
public class MyCompanyController {

    private final CompanyService companyService;
    private final CurrentUser currentUser;

    @GetMapping
    public CompanyDto.Response get() {
        return companyService.findCurrent();
    }

    /** Solo ADMIN (ver SecurityConfig). */
    @PutMapping
    public CompanyDto.Response update(@Valid @RequestBody CompanyDto.Request request) {
        return companyService.updateCurrent(request);
    }

    /** Franjas de recogida de un dia: /api/company/slots?date=2026-10-20 */
    @GetMapping("/slots")
    public List<CompanyDto.Slot> slots(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return companyService.getSlots(currentUser.companyId(), date);
    }
}
