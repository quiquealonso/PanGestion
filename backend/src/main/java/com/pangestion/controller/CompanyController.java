package com.pangestion.controller;

import com.pangestion.dto.CompanyDto;
import com.pangestion.service.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Alta y gestion de empresas (panaderias). Solo SUPERADMIN (ver SecurityConfig). */
@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    public List<CompanyDto.Response> findAll() {
        return companyService.findAll();
    }

    @GetMapping("/{id}")
    public CompanyDto.Response findById(@PathVariable Long id) {
        return companyService.findById(id);
    }

    /** 201 con la cabecera Location del recurso creado. */
    @PostMapping
    public ResponseEntity<CompanyDto.Response> create(@Valid @RequestBody CompanyDto.Request request) {
        CompanyDto.Response created = companyService.create(request);
        return ResponseEntity.created(Locations.child(created.id())).body(created);
    }

    @PutMapping("/{id}")
    public CompanyDto.Response update(@PathVariable Long id, @Valid @RequestBody CompanyDto.Request request) {
        return companyService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        companyService.delete(id);
    }
}
