package es.uclm.esibuy.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// Activa @Async con el executor que configura Spring Boot por defecto.
@Configuration
@EnableAsync
public class AsyncConfiguration {
}
