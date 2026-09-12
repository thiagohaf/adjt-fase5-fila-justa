package com.filajusta.matching.infrastructure.persistence;

import com.filajusta.matching.application.command.AlocacaoRepositorio;
import com.filajusta.matching.application.command.PacienteJaAlocadoException;
import com.filajusta.matching.application.command.RecursoJaAlocadoException;
import com.filajusta.matching.domain.Alocacao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prova, contra um Postgres 18 real via Testcontainers, que os 2 índices
 * únicos parciais de {@code matching_alocacao.alocacao}
 * ({@code V5__create_alocacao.sql}) REALMENTE rejeitam uma segunda
 * confirmação ativa para o mesmo {@code recursoId}/{@code pacienteId}
 * (Boundaries da spec 3-3b1: a constraint do banco é a única fonte de
 * verdade sob concorrência, nunca checagem em memória) -- {@code
 * ConfirmarAlocacaoTest} (unitário, com mocks) só prova a orquestração,
 * não a constraint real.
 */
@Testcontainers
@SpringBootTest
@TestPropertySource(properties = {
        "filajusta.matching.relay.enabled=false",
        "filajusta.matching.outbox-relay.enabled=false"
})
class AlocacaoRepositorioAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private AlocacaoRepositorio repositorio;

    private static Alocacao novaAlocacao(UUID recursoId, long pacienteId) {
        return new Alocacao(UUID.randomUUID(), recursoId, pacienteId, Alocacao.STATUS_ATIVA, Instant.now());
    }

    // pacienteId proprio por teste (indice unico parcial no banco) -- evita
    // colisao entre metodos de teste que rodam na mesma instancia do
    // Postgres/Spring context (mesmo raciocinio do codigoRecurso proprio por
    // teste em RecursoRepositorioAdapterIntegrationTest).
    private static long novoPacienteId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    @Test
    void confirmarUmaAlocacaoNovaPersisteEDevolveOMesmoCandidato() {
        long pacienteId = novoPacienteId();
        Alocacao candidata = novaAlocacao(UUID.randomUUID(), pacienteId);

        Alocacao confirmada = repositorio.confirmar(candidata);

        assertThat(confirmada.getAlocacaoId()).isEqualTo(candidata.getAlocacaoId());
        assertThat(confirmada.getRecursoId()).isEqualTo(candidata.getRecursoId());
        assertThat(confirmada.getPacienteId()).isEqualTo(pacienteId);
    }

    @Test
    void segundaConfirmacaoParaOMesmoRecursoIdAtivoLancaRecursoJaAlocado() {
        UUID recursoId = UUID.randomUUID();
        repositorio.confirmar(novaAlocacao(recursoId, novoPacienteId()));

        assertThatThrownBy(() -> repositorio.confirmar(novaAlocacao(recursoId, novoPacienteId())))
                .isInstanceOf(RecursoJaAlocadoException.class)
                .hasMessageContaining(recursoId.toString());
    }

    @Test
    void segundaConfirmacaoParaOMesmoPacienteIdAtivoLancaPacienteJaAlocado() {
        long pacienteId = novoPacienteId();
        repositorio.confirmar(novaAlocacao(UUID.randomUUID(), pacienteId));

        assertThatThrownBy(() -> repositorio.confirmar(novaAlocacao(UUID.randomUUID(), pacienteId)))
                .isInstanceOf(PacienteJaAlocadoException.class)
                .hasMessageContaining(String.valueOf(pacienteId));
    }

    @Test
    void confirmacoesComRecursoIdEPacienteIdTotalmenteDiferentesNaoColidem() {
        Alocacao primeira = repositorio.confirmar(novaAlocacao(UUID.randomUUID(), novoPacienteId()));
        Alocacao segunda = repositorio.confirmar(novaAlocacao(UUID.randomUUID(), novoPacienteId()));

        assertThat(segunda.getAlocacaoId()).isNotEqualTo(primeira.getAlocacaoId());
    }
}
