package com.pangestion.controller;

import com.pangestion.events.LiveEventsHub;
import com.pangestion.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Avisos en tiempo real (Server-Sent Events): GET /api/events deja la conexion abierta y va enviando
 * los eventos de la empresa del usuario ("change" y "ready"). El token va en la cabecera Authorization,
 * como en el resto de la API (el navegador lo lee con fetch, no con EventSource).
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventsController {

    private final LiveEventsHub hub;
    private final CurrentUser currentUser;

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        return hub.subscribe(currentUser.companyId());
    }
}
