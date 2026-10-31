package com.pangestion.security;

import com.pangestion.exception.TooManyRequestsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Limite de intentos por clave en una ventana de tiempo (login y pedidos publicos). */
@DisplayName("Unidad · RateLimiter")
class RateLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(now.get());
        }
    };
    private final RateLimiter limiter = new RateLimiter(true, clock);

    @Test
    @DisplayName("deja pasar hasta el maximo y luego 429 con los segundos que faltan")
    void limit() {
        for (int i = 0; i < 3; i++) {
            limiter.check("ip:1", 3, Duration.ofMinutes(1));
        }
        now.addAndGet(20_000);
        assertThatThrownBy(() -> limiter.check("ip:1", 3, Duration.ofMinutes(1)))
                .isInstanceOfSatisfying(TooManyRequestsException.class, e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(40));
    }

    @Test
    @DisplayName("cada clave cuenta aparte y la ventana se reinicia al pasar el tiempo")
    void keysAndWindow() {
        limiter.check("ip:1", 1, Duration.ofMinutes(1));
        assertThatCode(() -> limiter.check("ip:2", 1, Duration.ofMinutes(1))).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.check("ip:1", 1, Duration.ofMinutes(1))).isInstanceOf(TooManyRequestsException.class);
        now.addAndGet(60_000);
        assertThatCode(() -> limiter.check("ip:1", 1, Duration.ofMinutes(1))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("desactivado (tests y desarrollo) no limita")
    void disabled() {
        RateLimiter off = new RateLimiter(false, clock);
        assertThatCode(() -> {
            for (int i = 0; i < 100; i++) {
                off.check("ip:1", 1, Duration.ofMinutes(1));
            }
        }).doesNotThrowAnyException();
    }
}
