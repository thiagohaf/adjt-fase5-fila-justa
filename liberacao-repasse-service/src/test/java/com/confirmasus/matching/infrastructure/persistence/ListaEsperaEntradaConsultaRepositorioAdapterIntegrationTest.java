package com.confirmasus.matching.infrastructure.persistence;

import com.confirmasus.matching.application.query.ListaEsperaEntradaConsultaRepositorio;
import com.confirmasus.matching.domain.ListaEsperaEntrada;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova, contra um Postgres 18 real via Testcontainers, que {@link
 * ListaEsperaEntradaConsultaRepositorio#listarPorRecursoOrdenadoPorCriadoEm}
 * devolve as entradas de um Recurso ordenadas por {@code criadoEm}
 * ascendente (FIFO pura, AD-6) e isoladas por {@code recursoId} -- base da
 * Sugestão de Repasse em {@code ConsultarSugestaoRecurso}.
 */
@Testcontainers
@SpringBootTest
@TestPropertySource(properties = {
        "confirmasus.matching.relay.enabled=false",
        "confirmasus.matching.outbox-relay.enabled=false"
})
class ListaEsperaEntradaConsultaRepositorioAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private ListaEsperaEntradaConsultaRepositorio consultaRepositorio;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long novoPaciente() {
        String cpf = String.valueOf(10000000000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 89999999999L));
        return jdbcTemplate.queryForObject(
                "INSERT INTO matching_alocacao.paciente (cpf) VALUES (?) RETURNING paciente_id",
                Long.class, cpf);
    }

    private void novaEntrada(long pacienteId, UUID recursoId, Instant criadoEm) {
        jdbcTemplate.update(
                "INSERT INTO matching_alocacao.lista_espera_entrada "
                        + "(paciente_id, recurso_id, data_solicitacao, criado_em) VALUES (?, ?, ?, ?)",
                pacienteId, recursoId, Timestamp.from(criadoEm), Timestamp.from(criadoEm));
    }

    @Test
    void devolveEntradasDoRecursoOrdenadasPorCriadoEmAscendente() {
        UUID recursoId = UUID.randomUUID();
        long pacienteMaisRecente = novoPaciente();
        long pacienteMaisAntigo = novoPaciente();
        // Insercao fora de ordem, de proposito -- a ordenacao tem que vir
        // da query, nunca da ordem de insercao.
        novaEntrada(pacienteMaisRecente, recursoId, Instant.parse("2026-09-11T12:00:00Z"));
        novaEntrada(pacienteMaisAntigo, recursoId, Instant.parse("2026-09-11T09:00:00Z"));

        List<ListaEsperaEntrada> entradas = consultaRepositorio.listarPorRecursoOrdenadoPorCriadoEm(recursoId);

        assertThat(entradas).extracting(ListaEsperaEntrada::getPacienteId)
                .containsExactly(pacienteMaisAntigo, pacienteMaisRecente);
    }

    @Test
    void isolaEntradasPorRecursoId() {
        UUID recursoId = UUID.randomUUID();
        UUID outroRecursoId = UUID.randomUUID();
        long pacienteDoRecurso = novoPaciente();
        long pacienteDoOutroRecurso = novoPaciente();
        novaEntrada(pacienteDoRecurso, recursoId, Instant.parse("2026-09-11T09:00:00Z"));
        novaEntrada(pacienteDoOutroRecurso, outroRecursoId, Instant.parse("2026-09-11T08:00:00Z"));

        List<ListaEsperaEntrada> entradas = consultaRepositorio.listarPorRecursoOrdenadoPorCriadoEm(recursoId);

        assertThat(entradas).extracting(ListaEsperaEntrada::getPacienteId).containsExactly(pacienteDoRecurso);
    }

    @Test
    void recursoSemNinguemNaListaDeEsperaDevolveListaVazia() {
        List<ListaEsperaEntrada> entradas =
                consultaRepositorio.listarPorRecursoOrdenadoPorCriadoEm(UUID.randomUUID());

        assertThat(entradas).isEmpty();
    }
}
