package com.pangestion.security;

import com.pangestion.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite de peticiones por clave (IP, email...) en una ventana de tiempo fija, en memoria.
 * Frena la fuerza bruta en el login y los pedidos automaticos en la web publica (HTTP 429).
 * Al ser en memoria se reinicia con la API; para una sola instancia es suficiente.
 */
@Component
public class RateLimiter {

    private static final int MAX_KEYS = 10_000;

    private final boolean enabled;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public RateLimiter(@Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this(enabled, Clock.systemUTC());
    }

    RateLimiter(boolean enabled, Clock clock) {
        this.enabled = enabled;
        this.clock = clock;
    }

    /** Cuenta un intento con esa clave; lanza TooManyRequestsException si ya se han hecho max en la ventana. */
    public void check(String key, int max, Duration window) {
        if (!enabled) {
            return;
        }
        long now = clock.millis();
        if (windows.size() > MAX_KEYS) {
            windows.values().removeIf(w -> w.resetAt <= now);
        }
        Window current = windows.compute(key, (k, w) ->
                w == null || w.resetAt <= now ? new Window(now + window.toMillis(), 1) : new Window(w.resetAt, w.count + 1));
        if (current.count > max) {
            long seconds = Math.max(1, (current.resetAt - now + 999) / 1000);
            throw new TooManyRequestsException(seconds);
        }
    }

    private record Window(long resetAt, int count) {
    }
}
