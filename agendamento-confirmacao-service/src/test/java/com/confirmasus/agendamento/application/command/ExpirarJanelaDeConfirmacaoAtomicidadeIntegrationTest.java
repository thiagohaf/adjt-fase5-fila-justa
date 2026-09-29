package com.confirmasus.agendamento.application.command;

import com.confirmasus.agendamento.domain.Agendamento;
import com.confirmasus.agendamento.domain.EventoOutbox;
import com.confirmasus.agendamento.domain.StatusAgendamento;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;

/**
 * Prova, contra Postgres real, que UPDATE de status e os dois eventos do
 * outbox sao atomicos em {@code processar()}: falha ao gravar
 * {@code VagaLiberada} (2o evento) reverte o UPDATE e o 1o evento.
 * Regressao do {@code @Transactional} ignorado por auto-invocacao.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "confirmasus.agendamento.outbox-relay.enabled=false",
        "spring.task.scheduling.pool.size=1",
        "confirmasus.agendamento.expiracao-janela.poll-interval-ms=3600000"
})
class ExpirarJanelaDeConfirmacaoAtomicidadeIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private ExpirarJanelaDeConfirmacao poller;

    @Autowired
    private AgendamentoRepositorio agendamentoRepositorio;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private EventoOutboxRepositorio eventoOutboxRepositorio;

    @Test
    void falhaNoSegundoEventoReverteUpdateEPrimeiroEvento() {
        Instant agora = Instant.now();
        String cpf = String.format("%011d", Math.abs(System.nanoTime()) % 100_000_000_000L);
        Long pacienteId = jdbcTemplate.queryForObject(
                "INSERT INTO agendamento_confirmacao.pacientes (cpf) VALUES (?) RETURNING id",
                Long.class, cpf);
        Agendamento salvo = agendamentoRepositorio.salvar(new Agendamento(
                null, UUID.randomUUID(), pacienteId, UUID.randomUUID(),
                agora.plus(Duration.ofDays(1)),
                StatusAgendamento.AGUARDANDO_CONFIRMACAO,
                agora.minus(Duration.ofHours(2)),
                agora.minus(Duration.ofHours(1)),
                agora.minus(Duration.ofMinutes(1))));

        Mockito.doCallRealMethod()
                .doThrow(new RuntimeException("falha simulada no 2o evento"))
                .when(eventoOutboxRepositorio)
                .salvar(argThat((EventoOutbox e) -> e != null));

        poller.expirarJanelas();

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM agendamento_confirmacao.agendamentos WHERE id = ?",
                String.class, salvo.getId());
        Integer eventos = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agendamento_confirmacao.eventos_outbox "
                        + "WHERE payload ->> 'agendamentoId' = ?",
                Integer.class, String.valueOf(salvo.getId()));

        assertThat(status).isEqualTo("AGUARDANDO_CONFIRMACAO");
        assertThat(eventos).isZero();
    }
}
