package com.pangestion.events;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Conexiones SSE abiertas, agrupadas por empresa. Cada pantalla (cocina, sala, comanda) abre una
 * y recibe los LiveEvent de su empresa. Los eventos se envian solo cuando la transaccion que los
 * produjo se confirma, para no avisar de cambios que luego se deshacen.
 */
@Slf4j
@Component
public class LiveEventsHub {

    /** Las conexiones se cierran cada 30 minutos; el navegador vuelve a conectar solo. */
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long companyId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> list = emitters.computeIfAbsent(companyId, id -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        emitter.onCompletion(() -> list.remove(emitter));
        emitter.onTimeout(() -> list.remove(emitter));
        emitter.onError(e -> list.remove(emitter));
        // Primer mensaje: confirma la conexion al navegador
        send(companyId, emitter, SseEmitter.event().name("hello").data("ok"));
        return emitter;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onEvent(LiveEvent event) {
        broadcast(event);
    }

    /** Envia el evento a todas las pantallas de la empresa. */
    public void broadcast(LiveEvent event) {
        for (SseEmitter emitter : emitters.getOrDefault(event.companyId(), List.of())) {
            send(event.companyId(), emitter, SseEmitter.event().name(event.name()).data(event.data(), MediaType.APPLICATION_JSON));
        }
    }

    /** Latido cada 25 segundos: mantiene vivas las conexiones y descubre las que se han cortado. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        emitters.forEach((companyId, list) -> list.forEach(emitter -> send(companyId, emitter, SseEmitter.event().comment("ping"))));
    }

    /** Conexiones abiertas de una empresa (para los tests). */
    int connections(Long companyId) {
        return emitters.getOrDefault(companyId, List.of()).size();
    }

    private void send(Long companyId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            // Pantalla cerrada o conexion caida: se olvida
            emitters.getOrDefault(companyId, List.of()).remove(emitter);
            log.debug("Conexion SSE cerrada: {}", e.getMessage());
        }
    }
}
