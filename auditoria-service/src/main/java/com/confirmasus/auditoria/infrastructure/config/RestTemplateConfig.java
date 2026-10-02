package com.confirmasus.auditoria.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Bean {@link RestTemplate} usado por {@code PacienteClient} para resolver
 * dados de exibição do paciente (nome/CPF) chamando {@code
 * agendamento-confirmacao-service} -- faltava este bean (a aplicação nunca
 * subia: {@code
 * UnsatisfiedDependencyException} em {@code PacienteClient}, achado ao
 * validar {@code GET /v1/auditoria/agendamento/{id}} end-to-end).
 */
@Configuration
class RestTemplateConfig {

    @Bean
    RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
