package com.confirmasus.agendamento.application.command;

import com.confirmasus.agendamento.domain.Agendamento;
import com.confirmasus.agendamento.domain.MotivoLiberacao;
import com.confirmasus.agendamento.domain.StatusAgendamento;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
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
 * Prova, contra um Postgres 18 real via Testcontainers, o cenario
 * "Multiplas tasks ECS concorrentes" da I/O &amp; Edge-Case Matrix da spec
 * 1.5: "2+ instancias rodando o mesmo poller de expiracao -&gt; SKIP LOCKED
 * garante que cada linha e processada por uma unica instancia". Analogon de
 * {@code AbrirJanelaDeConfirmacaoConcurrencyIntegrationTest} -- N threads
 * chamando {@code poller.expirarJanelas()} concorrentemente no MESMO bean
 * Spring simula "N instancias do poller disparando ao mesmo tempo".
 * {@code batch-size=1}: cada chamada so pode enxergar 1 linha pendente por
 * vez, isolando a corrida na UNICA linha pendente do teste.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "confirmasus.agendamento.outbox-relay.enabled=false",
        "confirmasus.agendamento.expiracao-janela.batch-size=1"
})
class ExpirarJanelaDeConfirmacaoConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private ExpirarJanelaDeConfirmacao poller;

    @Autowired
    private AgendamentoRepositorio agendamentoRepositorio;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void execucoesConcorrentesSobreOMesmoAgendamentoApenasUmaTransicionaEGravaDoiseventos() throws Exception {
        Instant agora = Instant.now();
        Instant janelaExpiraEm = agora.minus(Duration.ofMinutes(1));
        Instant dataHoraAgendamento = agora.plus(Duration.ofDays(1));

        Agendamento pendente = new Agendamento(
                null,
                criarPacienteERetornarId(),
                UUID.randomUUID(),
                dataHoraAgendamento,
                StatusAgendamento.AGUARDANDO_CONFIRMACAO,
                agora.minus(Duration.ofHours(2)),
                agora.minus(Duration.ofHours(1)),
                janelaExpiraEm);
        Agendamento salvo = agendamentoRepositorio.salvar(pendente);

        int totalThreads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        try {
            CountDownLatch partida = new CountDownLatch(1);
            List<Future<Void>> resultados = new ArrayList<>();
            for (int i = 0; i < totalThreads; i++) {
                resultados.add(executor.submit(() -> {
                    partida.await();
                    poller.expirarJanelas();
                    return null;
                }));
            }

            partida.countDown();
            List<Exception> falhas = new ArrayList<>();
            for (Future<Void> resultado : resultados) {
                try {
                    resultado.get(15, TimeUnit.SECONDS);
                } catch (Exception e) {
                    falhas.add(e);
                }
            }
            assertThat(falhas)
                    .as("nenhuma execucao concorrente do poller pode propagar excecao")
                    .isEmpty();
        } finally {
            executor.shutdown();
        }

        // Verifica que o Agendamento foi transicionado exatamente uma vez
        String statusFinal = jdbcTemplate.queryForObject(
                "SELECT status FROM agendamento_confirmacao.agendamentos WHERE id = ?",
                String.class, salvo.getId());
        assertThat(statusFinal)
                .as("exatamente 1 das %d execucoes concorrentes pode transicionar o MESMO Agendamento",
                        totalThreads)
                .isEqualTo("LIBERADO");

        String motivoFinal = jdbcTemplate.queryForObject(
                "SELECT motivo_liberacao FROM agendamento_confirmacao.agendamentos WHERE id = ?",
                String.class, salvo.getId());
        assertThat(motivoFinal)
                .isEqualTo(MotivoLiberacao.NAO_CONFIRMADO.name());

        // Verifica que exatamente 2 eventos foram gravados (AgendamentoNaoConfirmado + VagaLiberada)
        Integer eventosGravados = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agendamento_confirmacao.eventos_outbox "
                        + "WHERE payload ->> 'agendamentoId' = ?",
                Integer.class, String.valueOf(salvo.getId()));
        assertThat(eventosGravados)
                .as("exatamente 2 eventos (AgendamentoNaoConfirmado + VagaLiberada) devem ser gravados")
                .isEqualTo(2);
    }

    private Long criarPacienteERetornarId() {
        String cpf = String.format("%011d", Math.abs(System.nanoTime()) % 100_000_000_000L);
        return jdbcTemplate.queryForObject(
                "INSERT INTO agendamento_confirmacao.pacientes (cpf) VALUES (?) RETURNING id",
                Long.class, cpf);
    }
}
