package com.confirmasus.matching.infrastructure.persistence;

import com.confirmasus.matching.application.command.AlocacaoRepositorio;
import com.confirmasus.matching.application.command.EventoOutboxRepositorio;
import com.confirmasus.matching.application.command.LiberarRecurso;
import com.confirmasus.matching.application.command.RecursoRepositorio;
import com.confirmasus.matching.domain.Alocacao;
import com.confirmasus.matching.domain.Recurso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova, contra um Postgres 18 real via Testcontainers, a idempotência sob
 * concorrência real de {@link LiberarRecurso} (Story 3-4b1): N chamadas
 * concorrentes de {@code liberar} para a MESMA {@code alocacaoId} devem
 * produzir exatamente 1 update efetivo (ATIVA -&gt; LIBERADA, Recurso volta a
 * ficar disponível) e exatamente 1 linha de evento {@code RecursoLiberado}
 * gravada no outbox -- mesmo padrão de
 * {@link UltimaSugestaoRegistradaRepositorioAdapterIntegrationTest}
 * (ExecutorService/CountDownLatch contra um Postgres real, não um mock).
 *
 * <p>{@link LiberarRecurso} não é um bean Spring nesta story (sem
 * consumidor ainda -- Story 3-4b2, deferida) -- instanciado diretamente
 * aqui com os adapters reais ({@link AlocacaoRepositorio},
 * {@link RecursoRepositorio}, {@link EventoOutboxRepositorio}) injetados
 * pelo contexto de teste, o mesmo padrão de {@code ConfirmarAlocacaoTest}
 * (que também instancia o comando manualmente, só que lá com mocks; aqui
 * com os adapters reais contra o Postgres do Testcontainers).
 *
 * <p>A checagem de status pós-teste usa
 * {@link AlocacaoJpaRepository#pacientesComAlocacaoAtiva(String)} (já
 * existente) em vez de um getter novo em {@link AlocacaoJpaEntity} --
 * aquela entidade deliberadamente não expõe getters (javadoc da classe: só
 * existe para satisfazer o parâmetro genérico de
 * {@link AlocacaoJpaRepository}).
 */
@Testcontainers
@SpringBootTest
@TestPropertySource(properties = {
        "confirmasus.matching.relay.enabled=false",
        "confirmasus.matching.outbox-relay.enabled=false",
        "confirmasus.matching.liberacao-agendada-relay.enabled=false"
})
class LiberarRecursoRepositorioAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private AlocacaoRepositorio alocacaoRepositorio;

    @Autowired
    private RecursoRepositorio recursoRepositorio;

    @Autowired
    private EventoOutboxRepositorio eventoOutboxRepositorio;

    @Autowired
    private AlocacaoJpaRepository alocacaoJpaRepository;

    @Autowired
    private RecursoJpaRepository recursoJpaRepository;

    @Autowired
    private EventoOutboxJpaRepository eventoOutboxJpaRepository;

    @Autowired
    private Clock clock;

    private LiberarRecurso liberarRecurso;

    @BeforeEach
    void configurar() {
        eventoOutboxJpaRepository.deleteAll();
        alocacaoJpaRepository.deleteAll();
        recursoJpaRepository.deleteAll();
        liberarRecurso = new LiberarRecurso(alocacaoRepositorio, recursoRepositorio, eventoOutboxRepositorio, clock);
    }

    @Test
    void chamadasConcorrentesParaAMesmaAlocacaoProduzemApenasUmEfeito() throws Exception {
        UUID recursoId = UUID.randomUUID();
        UUID alocacaoId = UUID.randomUUID();
        long pacienteId = 42L;

        // Setup via portas (adapters @Transactional), nao via jpaRepository
        // diretamente -- @Modifying @Query custom so tem uma transacao ativa
        // quando chamado atraves do adapter que a declara (mesmo raciocinio
        // de UltimaSugestaoRegistradaRepositorioAdapterIntegrationTest, que
        // so usa o jpaRepository para leituras/deleteAll, nunca escrita).
        recursoRepositorio.upsert(new Recurso(recursoId, "LEITO-CONCORRENCIA-LIBERACAO", 1, false, null, null));
        alocacaoRepositorio.confirmar(new Alocacao(alocacaoId, recursoId, pacienteId, Alocacao.STATUS_ATIVA,
                Instant.now()));

        int totalThreads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        try {
            CountDownLatch partida = new CountDownLatch(1);
            List<Future<Void>> resultados = new ArrayList<>();
            for (int i = 0; i < totalThreads; i++) {
                resultados.add(executor.submit(() -> {
                    partida.await();
                    liberarRecurso.liberar(alocacaoId, recursoId, "corr-concorrencia-liberacao");
                    return null;
                }));
            }

            partida.countDown();
            for (Future<Void> resultado : resultados) {
                resultado.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
        }

        assertThat(alocacaoJpaRepository.pacientesComAlocacaoAtiva(Alocacao.STATUS_LIBERADA))
                .as("exatamente a Alocacao de teste deve estar LIBERADA apos as chamadas concorrentes")
                .containsExactly(pacienteId);
        assertThat(alocacaoJpaRepository.pacientesComAlocacaoAtiva(Alocacao.STATUS_ATIVA))
                .as("nenhuma Alocacao ATIVA deve sobrar -- a unica existente foi liberada")
                .doesNotContain(pacienteId);
        assertThat(recursoJpaRepository.findById(recursoId))
                .hasValueSatisfying(entidade -> assertThat(entidade.isDisponivel()).isTrue());

        List<EventoOutboxJpaEntity> eventos = eventoOutboxJpaRepository.findAll();
        assertThat(eventos)
                .as("exatamente 1 evento RecursoLiberado deve ser gravado, apesar de %d chamadas concorrentes"
                        + " para a mesma alocacaoId", totalThreads)
                .hasSize(1);
        assertThat(eventos.get(0).getEventType()).isEqualTo("RecursoLiberado");
    }
}
