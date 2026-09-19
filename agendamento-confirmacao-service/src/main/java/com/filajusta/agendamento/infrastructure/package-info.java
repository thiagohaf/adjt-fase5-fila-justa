/**
 * Camada de infraestrutura do agendamento-confirmacao-service. Actuator
 * expoe {@code GET /actuator/health} (porta de management separada, ver
 * application.yml). {@code infrastructure.web} expoe {@code POST
 * /v1/agendamentos} (Story 1.1); {@code infrastructure.persistence} mapeia
 * o schema {@code agendamento_confirmacao} (JPA + migration Flyway, AD-9).
 *
 * <p>Deferido nesta fase (Boundaries da spec 1.1 -- "Never"): {@code
 * infrastructure.grpc} ({@code ResolverOuCriarPaciente} exposto via gRPC,
 * consumido por {@code liberacao-repasse-service}, adiado para Story 2.1) e
 * qualquer publisher de evento de dominio via outbox/SNS (nenhum evento
 * definido para o registro do Agendamento em si, AD-3 -- outbox entra a
 * partir da Story 1.2).
 */
package com.filajusta.agendamento.infrastructure;
