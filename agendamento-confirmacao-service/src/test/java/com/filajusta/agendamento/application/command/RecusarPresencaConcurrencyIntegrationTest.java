package com.filajusta.agendamento.application.command;

import com.filajusta.agendamento.domain.Agendamento;
import com.filajusta.agendamento.domain.MotivoLiberacao;
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
 * Prova, contra um Postgres 18 real via Testcontainers, o cenario "Corrida
 * Recusa vs. Recusa" da I/O &amp; Edge-Case Matrix da spec 1.4: N
 * requisicoes concorrentes de recusa para o MESMO {@code agendamentoId}
 * -- a escrita condicional (AD-4) garante que so uma vence; as demais releem
 * o estado ja {@code LIBERADO/RECUSA} e recebem sucesso silencioso, nunca uma
 * excecao. Molde de {@code ConfirmarPresencaConcurrencyIntegrationTest}
 * -- N threads chamando {@code recusarPresenca.recusar(id)}
 * concorrentemente no MESMO bean Spring simula N requisicoes HTTP
 * concorrentes: cada chamada, por ser {@code @Transactional} (proxy Spring),
 * abre sua PROPRIA transacao/conexao.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "filajusta.agendamento.outbox-relay.enabled=false")
class RecusarPresencaConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private RecusarPresenca recusarPresenca;

    @Autowired
    private ConfirmarPresenca confirmarPresenca;

    @Autowired
    private AgendamentoRepositorio agendamentoRepositorio;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void execucoesConcorrentesSobreOMesmoAgendamentoApenasUmaGravaRecusaRegistradaEVagaLiberada() throws Exception {
        Instant agora = Instant.now();
        Agendamento aguardandoConfirmacao = new Agendamento(
                null, criarPacienteERetornarId(), UUID.randomUUID(), agora.plus(Duration.ofDays(1)),
                StatusAgendamento.AGUARDANDO_CONFIRMACAO, agora.minus(Duration.ofHours(1)),
                agora.minus(Duration.ofMinutes(10)), null);
        Agendamento salvo = agendamentoRepositorio.salvar(aguardandoConfirmacao);

        int totalThreads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        try {
            CountDownLatch partida = new CountDownLatch(1);
            List<Future<Void>> resultados = new ArrayList<>();
            for (int i = 0; i < totalThreads; i++) {
                resultados.add(executor.submit(() -> {
                    partida.await();
                    recusarPresenca.recusar(salvo.getId());
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
                    .as("nenhuma recusa concorrente do mesmo agendamentoId pode propagar excecao -- "
                            + "a perdedora releh o estado ja LIBERADO/RECUSA e retorna sucesso silencioso")
                    .isEmpty();
        } finally {
            executor.shutdown();
        }

        Integer statusFinal = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agendamento_confirmacao.agendamentos "
                        + "WHERE id = ? AND status = 'LIBERADO' AND motivo_liberacao = ?",
                Integer.class, salvo.getId(), MotivoLiberacao.RECUSA.name());
        assertThat(statusFinal)
                .as("exatamente 1 das %d recusas concorrentes pode transicionar o MESMO Agendamento",
                        totalThreads)
                .isEqualTo(1);

        Integer eventosGravados = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agendamento_confirmacao.eventos_outbox "
                        + "WHERE payload ->> 'agendamentoId' = ?",
                Integer.class, String.valueOf(salvo.getId()));
        assertThat(eventosGravados)
                .as("exatamente 2 eventos podem ser gravados para a mesma Recusa: "
                        + "1 RecusaRegistrada + 1 VagaLiberada (nenhuma segunda dupla de eventos)")
                .isEqualTo(2);
    }

    @Test
    void execucoesConcorrentesRecusaVsConfirmacaoSobreOMesmoAgendamento() throws Exception {
        Instant agora = Instant.now();
        Agendamento aguardandoConfirmacao = new Agendamento(
                null, criarPacienteERetornarId(), UUID.randomUUID(), agora.plus(Duration.ofDays(1)),
                StatusAgendamento.AGUARDANDO_CONFIRMACAO, agora.minus(Duration.ofHours(1)),
                agora.minus(Duration.ofMinutes(10)), null);
        Agendamento salvo = agendamentoRepositorio.salvar(aguardandoConfirmacao);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch partida = new CountDownLatch(1);
            List<Future<Void>> resultados = new ArrayList<>();

            // Thread 1: recusa
            resultados.add(executor.submit(() -> {
                partida.await();
                try {
                    recusarPresenca.recusar(salvo.getId());
                } catch (AgendamentoForaDaJanelaException e) {
                    // Esperado: uma das operacoes vence, a outra recebe 409
                }
                return null;
            }));

            // Thread 2: confirmacao
            resultados.add(executor.submit(() -> {
                partida.await();
                try {
                    confirmarPresenca.confirmar(salvo.getId());
                } catch (AgendamentoForaDaJanelaException e) {
                    // Esperado: uma das operacoes vence, a outra recebe 409
                }
                return null;
            }));

            partida.countDown();
            for (Future<Void> resultado : resultados) {
                resultado.get(15, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
        }

        // Verificar que apenas uma transicao venceu
        Integer liberados = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agendamento_confirmacao.agendamentos "
                        + "WHERE id = ? AND status = 'LIBERADO'",
                Integer.class, salvo.getId());
        Integer confirmados = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agendamento_confirmacao.agendamentos "
                        + "WHERE id = ? AND status = 'CONFIRMADO'",
                Integer.class, salvo.getId());

        assertThat(liberados + confirmados)
                .as("exatamente uma das operacoes concorrentes (recusa ou confirmacao) deve vencer")
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
