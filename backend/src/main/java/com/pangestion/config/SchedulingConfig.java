package com.pangestion.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Tareas periodicas (latido de las conexiones de avisos en tiempo real). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
