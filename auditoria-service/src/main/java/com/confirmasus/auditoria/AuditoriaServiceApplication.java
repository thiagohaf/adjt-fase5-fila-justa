package com.confirmasus.auditoria;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Aplicação Spring Boot para auditoria-service (Story 4.1).
 *
 * <p>Registra decisões auditáveis consumindo eventos via SQS FIFO.
 * Faz parte da solução para o problema: "Nenhuma decisão do sistema é
 * rastreável hoje -- falta o componente 'auditoria-service'".
 *
 * <p>{@code @EnableScheduling} ativa o consumer job {@link
 * com.confirmasus.auditoria.infrastructure.relay.DecisaoSqsConsumerJob}
 * (que consome a fila periodicamente via {@code @Scheduled}).
 */
@SpringBootApplication
@EnableScheduling
public class AuditoriaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditoriaServiceApplication.class, args);
    }

    /**
     * Fornece um {@link Clock} injetável para os componentes.
     * Permite testabilidade (mock de tempo em testes).
     *
     * @return clock do sistema em UTC
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
