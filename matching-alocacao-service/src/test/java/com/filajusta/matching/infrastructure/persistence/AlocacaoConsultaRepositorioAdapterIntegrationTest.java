package com.filajusta.matching.infrastructure.persistence;

import com.filajusta.matching.application.command.AlocacaoRepositorio;
import com.filajusta.matching.application.query.AlocacaoConsultaRepositorio;
import com.filajusta.matching.domain.Alocacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova, contra um Postgres 18 real via Testcontainers, que {@link
 * AlocacaoJpaRepository#pacientesComAlocacaoAtiva} (Story 3-3b2a, {@code
 * SELECT paciente_id ... WHERE status = :status} nativo) devolve os {@code
 * pacienteId} de toda {@link Alocacao} ATIVA persistida via {@link
 * AlocacaoRepositorio#confirmar} -- I/O Matrix da spec 3-3b2a, mesmo padrão
 * de {@link AlocacaoRepositorioAdapterIntegrationTest}/{@link
 * RecursoConsultaRepositorioAdapterIntegrationTest}.
 *
 * <p>{@link #limparAlocacoes()} trunca a tabela antes de cada teste --
 * mesmo padrão de {@link RecursoConsultaRepositorioAdapterIntegrationTest},
 * necessário aqui porque {@code pacientesComAlocacaoAtiva()} agrega TODAS as
 * linhas ATIVA da tabela (não filtra por uma chave própria do teste), então
 * dados de um método anterior vazariam para a contagem do próximo. Isolamento
 * por estado, não por ordem: qualquer método (incluindo novos, futuros) vê
 * sempre uma tabela genuinamente vazia no início.
 */
@Testcontainers
@SpringBootTest
@TestPropertySource(properties = {
        "filajusta.matching.relay.enabled=false",
        "filajusta.matching.outbox-relay.enabled=false"
})
class AlocacaoConsultaRepositorioAdapterIntegrationTest {

    private static final String STATUS_CANCELADA = "CANCELADA";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private AlocacaoRepositorio alocacaoRepositorio;

    @Autowired
    private AlocacaoConsultaRepositorio alocacaoConsultaRepositorio;

    @Autowired
    private AlocacaoJpaRepository jpaRepository;

    @BeforeEach
    void limparAlocacoes() {
        jpaRepository.deleteAll();
    }

    private static Alocacao novaAlocacaoComStatus(long pacienteId, String status) {
        return new Alocacao(UUID.randomUUID(), UUID.randomUUID(), pacienteId, status, Instant.now());
    }

    private static Alocacao novaAlocacaoAtiva(long pacienteId) {
        return novaAlocacaoComStatus(pacienteId, Alocacao.STATUS_ATIVA);
    }

    // pacienteId proprio por teste (indice unico parcial no banco) -- mesmo
    // padrao de AlocacaoRepositorioAdapterIntegrationTest#novoPacienteId().
    // Mantido mesmo com o deleteAll() em @BeforeEach (defensivo, sem custo).
    private static long novoPacienteId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    @Test
    void tabelaVaziaDevolveSetVazio() {
        // Prova literal da linha "Nenhuma Alocacao no sistema -> Set vazio"
        // da I/O Matrix da spec 3-3b2a -- isEmpty(), nao apenas
        // doesNotContain(), senao um bug que ignorasse o WHERE e
        // devolvesse tudo ainda passaria. Genuinamente vazia gracas ao
        // deleteAll() do @BeforeEach, nao a ordem de execucao dos testes.
        Set<Long> pacientesAlocados = alocacaoConsultaRepositorio.pacientesComAlocacaoAtiva();

        assertThat(pacientesAlocados).isEmpty();
    }

    @Test
    void alocacaoAtivaExistenteApareceNoSetDePacientesAlocados() {
        long pacienteId = novoPacienteId();
        alocacaoRepositorio.confirmar(novaAlocacaoAtiva(pacienteId));

        Set<Long> pacientesAlocados = alocacaoConsultaRepositorio.pacientesComAlocacaoAtiva();

        assertThat(pacientesAlocados).contains(pacienteId);
    }

    @Test
    void multiplosPacientesAlocadosAparecemTodosNoSet() {
        long pacienteId1 = novoPacienteId();
        long pacienteId2 = novoPacienteId();
        alocacaoRepositorio.confirmar(novaAlocacaoAtiva(pacienteId1));
        alocacaoRepositorio.confirmar(novaAlocacaoAtiva(pacienteId2));

        Set<Long> pacientesAlocados = alocacaoConsultaRepositorio.pacientesComAlocacaoAtiva();

        assertThat(pacientesAlocados).contains(pacienteId1, pacienteId2);
    }

    @Test
    void apenasPacientesComAlocacaoAtivaAparecemNoSetOutroStatusENaoOWhereInteiro() {
        // Prova que o WHERE status = :status realmente discrimina -- sem
        // este teste, uma query sem WHERE nenhum (SELECT paciente_id FROM
        // alocacao) passaria igual nos demais testes desta classe, que so
        // inserem Alocacao.STATUS_ATIVA (achado do code review multi-agente
        // desta story).
        long pacienteIdAtivo = novoPacienteId();
        long pacienteIdCancelado = novoPacienteId();
        alocacaoRepositorio.confirmar(novaAlocacaoAtiva(pacienteIdAtivo));
        alocacaoRepositorio.confirmar(novaAlocacaoComStatus(pacienteIdCancelado, STATUS_CANCELADA));

        Set<Long> pacientesAlocados = alocacaoConsultaRepositorio.pacientesComAlocacaoAtiva();

        assertThat(pacientesAlocados).contains(pacienteIdAtivo);
        assertThat(pacientesAlocados).doesNotContain(pacienteIdCancelado);
    }
}
