package com.filajusta.agendamento.application.command;

import com.filajusta.agendamento.domain.Agendamento;
import com.filajusta.agendamento.domain.StatusAgendamento;
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
 * 1.2: "2+ instancias rodando o mesmo poller -&gt; SKIP LOCKED garante que
 * cada linha e processada por uma unica instancia". Molde de
 * {@code matching-alocacao-service/.../
 * LiberacaoAgendadaRelayJobConcurrencyIntegrationTest} -- N threads chamando
 * {@code poller.abrirJanelas()} concorrentemente no MESMO bean Spring
 * simula "N instancias do poller disparando ao mesmo tempo": cada chamada,
 * por ser {@code @Transactional} (proxy Spring), abre sua PROPRIA
 * transacao/conexao, exatamente a mesma condicao de corrida entre processos
 * distintos que {@code SELECT ... FOR UPDATE SKIP LOCKED} precisa resolver.
 * {@code batch-size=1}: cada chamada so pode enxergar 1 linha pendente por
 * vez, isolando a corrida na UNICA linha pendente do teste.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "filajusta.agendamento.outbox-relay.enabled=false",
        "filajusta.agendamento.abertura-janela.batch-size=1"
})
class AbrirJanelaDeConfirmacaoConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private AbrirJanelaDeConfirmacao poller;

    @Autowired
    private AgendamentoRepositorio agendamentoRepositorio;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void execucoesConcorrentesSobreOMesmoAgendamentoApenasUmaTransicionaEGravaUmaNotificacao() throws Exception {
        Instant agora = Instant.now();
        Agendamento pendente = Agendamento.novo(
                criarPacienteERetornarId(), UUID.randomUUID(), agora.plus(Duration.ofDays(1)), agora,
                agora.minus(Duration.ofMinutes(1)));
        Agendamento salvo = agendamentoRepositorio.salvar(pendente);

        int totalThreads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        try {
            CountDownLatch partida = new CountDownLatch(1);
            List<Future<Void>> resultados = new ArrayList<>();
            for (int i = 0; i < totalThreads; i++) {
                resultados.add(executor.submit(() -> {
                    partida.await();
                    poller.abrirJanelas();
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
                    .as("nenhuma execucao concorrente do poller pode propagar excecao "
                            + "(mesma garantia de nunca derrubar a app)")
                    .isEmpty();
        } finally {
            executor.shutdown();
        }

        Integer statusFinal = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agendamento_confirmacao.agendamentos "
                        + "WHERE id = ? AND status = 'AGUARDANDO_CONFIRMACAO'",
                Integer.class, salvo.getId());
        assertThat(statusFinal)
                .as("exatamente 1 das %d execucoes concorrentes pode transicionar o MESMO Agendamento -- "
                        + "SKIP LOCKED + escrita condicional fecham a corrida entre instancias do poller",
                        totalThreads)
                .isEqualTo(1);

        Integer eventosGravados = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agendamento_confirmacao.eventos_outbox "
                        + "WHERE payload ->> 'agendamentoId' = ?",
                Integer.class, String.valueOf(salvo.getId()));
        assertThat(eventosGravados)
                .as("nenhuma segunda NotificacaoConfirmacaoPublicada pode ser gravada para o mesmo Agendamento")
                .isEqualTo(1);
    }

    private Long criarPacienteERetornarId() {
        // CPF sintetico de 11 digitos, unico o bastante para este teste (nao
        // precisa ser um CPF matematicamente valido -- a tabela so exige
        // VARCHAR(11) UNIQUE, a validacao de digito verificador vive em
        // Cpf/domain, nao no schema).
        String cpf = String.format("%011d", Math.abs(System.nanoTime()) % 100_000_000_000L);
        return jdbcTemplate.queryForObject(
                "INSERT INTO agendamento_confirmacao.pacientes (cpf) VALUES (?) RETURNING id",
                Long.class, cpf);
    }
}
