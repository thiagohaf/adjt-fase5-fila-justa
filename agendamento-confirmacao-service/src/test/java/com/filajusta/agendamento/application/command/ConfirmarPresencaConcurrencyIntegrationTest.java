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
 * Prova, contra um Postgres 18 real via Testcontainers, o cenario "Corrida
 * Confirmacao vs. Confirmacao" da I/O &amp; Edge-Case Matrix da spec 1.3: N
 * requisicoes concorrentes de confirmacao para o MESMO {@code agendamentoId}
 * -- a escrita condicional (AD-4) garante que so uma vence; as demais releem
 * o estado ja {@code CONFIRMADO} e recebem sucesso silencioso, nunca uma
 * excecao. Molde de {@code AbrirJanelaDeConfirmacaoConcurrencyIntegrationTest}
 * -- N threads chamando {@code confirmarPresenca.confirmar(id)}
 * concorrentemente no MESMO bean Spring simula N requisicoes HTTP
 * concorrentes: cada chamada, por ser {@code @Transactional} (proxy Spring),
 * abre sua PROPRIA transacao/conexao.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "filajusta.agendamento.outbox-relay.enabled=false")
class ConfirmarPresencaConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private ConfirmarPresenca confirmarPresenca;

    @Autowired
    private AgendamentoRepositorio agendamentoRepositorio;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void execucoesConcorrentesSobreOMesmoAgendamentoApenasUmaGravaUmaConfirmacaoRegistrada() throws Exception {
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
                    confirmarPresenca.confirmar(salvo.getId());
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
                    .as("nenhuma confirmacao concorrente do mesmo agendamentoId pode propagar excecao -- "
                            + "a perdedora releh o estado ja CONFIRMADO e retorna sucesso silencioso")
                    .isEmpty();
        } finally {
            executor.shutdown();
        }

        Integer statusFinal = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agendamento_confirmacao.agendamentos "
                        + "WHERE id = ? AND status = 'CONFIRMADO'",
                Integer.class, salvo.getId());
        assertThat(statusFinal)
                .as("exatamente 1 das %d confirmacoes concorrentes pode transicionar o MESMO Agendamento",
                        totalThreads)
                .isEqualTo(1);

        Integer eventosGravados = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agendamento_confirmacao.eventos_outbox "
                        + "WHERE payload ->> 'agendamentoId' = ?",
                Integer.class, String.valueOf(salvo.getId()));
        assertThat(eventosGravados)
                .as("nenhuma segunda ConfirmacaoRegistrada pode ser gravada para o mesmo Agendamento")
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
