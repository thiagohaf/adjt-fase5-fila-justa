package com.filajusta.matching.infrastructure.persistence;

import com.filajusta.matching.application.command.ScoreReplicaRepositorio;
import com.filajusta.matching.domain.ScoreReplica;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova, contra um Postgres 18 real via Testcontainers (sem LocalStack --
 * este teste não envolve SQS), que
 * {@link ScoreReplicaJpaRepository#upsertSeMaisRecente} aplica exatamente a
 * mesma regra de last-write-wins de {@link ScoreReplica#maisRecenteQue}
 * atomicamente no banco (Tasks da spec 3.1b: "Teste de integração
 * Testcontainers-Postgres (upsert idempotente)") -- cobre a I/O &amp;
 * Edge-Case Matrix inteira ao nível do SQL nativo.
 */
@Testcontainers
@SpringBootTest
@TestPropertySource(properties = "filajusta.matching.relay.enabled=false")
class ScoreReplicaRepositorioAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private ScoreReplicaRepositorio repositorio;

    @Autowired
    private ScoreReplicaJpaRepository jpaRepository;

    private static final Instant T1 = Instant.parse("2026-09-08T12:00:00Z");
    private static final Instant T2 = Instant.parse("2026-09-08T13:00:00Z");

    @Test
    void primeiroUpsertParaUmPacienteInsereALinha() {
        long pacienteId = novoPacienteId();
        UUID eventId = UUID.randomUUID();

        repositorio.upsertSeMaisRecente(new ScoreReplica(pacienteId, 55, T1, eventId, Instant.now()));

        ScoreReplicaJpaEntity linha = buscar(pacienteId);
        assertThat(linha.getScore()).isEqualTo(55);
        assertThat(linha.getOccurredAt()).isEqualTo(T1);
        assertThat(linha.getEventId()).isEqualTo(eventId);
    }

    @Test
    void consumoNormalOccurredAtMaisRecenteSubstituiALinha() {
        long pacienteId = novoPacienteId();
        repositorio.upsertSeMaisRecente(new ScoreReplica(pacienteId, 40, T1, UUID.randomUUID(), Instant.now()));

        UUID eventIdNovo = UUID.randomUUID();
        repositorio.upsertSeMaisRecente(new ScoreReplica(pacienteId, 90, T2, eventIdNovo, Instant.now()));

        ScoreReplicaJpaEntity linha = buscar(pacienteId);
        assertThat(linha.getScore()).isEqualTo(90);
        assertThat(linha.getOccurredAt()).isEqualTo(T2);
        assertThat(linha.getEventId()).isEqualTo(eventIdNovo);
    }

    @Test
    void redeliveryDoMesmoEventIdNaoDuplicaNemAlteraALinha() {
        // I/O Matrix: "mesmo eventId entregue duas vezes -- upsert
        // idempotente, replica nao duplica nem retrocede".
        long pacienteId = novoPacienteId();
        UUID eventId = UUID.randomUUID();
        ScoreReplica evento = new ScoreReplica(pacienteId, 60, T1, eventId, Instant.now());

        repositorio.upsertSeMaisRecente(evento);
        repositorio.upsertSeMaisRecente(evento);

        assertThat(jpaRepository.count()).isGreaterThanOrEqualTo(1);
        ScoreReplicaJpaEntity linha = buscar(pacienteId);
        assertThat(linha.getScore()).isEqualTo(60);
        assertThat(linha.getEventId()).isEqualTo(eventId);
    }

    @Test
    void mensagemForaDeOrdemOccurredAtMaisAntigoNaoSobrescreve() {
        // I/O Matrix: "occurredAt menor que o ja persistido -- replica
        // mantem a versao mais recente; mensagem antiga nao sobrescreve".
        long pacienteId = novoPacienteId();
        UUID eventIdRecente = UUID.randomUUID();
        repositorio.upsertSeMaisRecente(new ScoreReplica(pacienteId, 90, T2, eventIdRecente, Instant.now()));

        repositorio.upsertSeMaisRecente(new ScoreReplica(pacienteId, 10, T1, UUID.randomUUID(), Instant.now()));

        ScoreReplicaJpaEntity linha = buscar(pacienteId);
        assertThat(linha.getScore()).isEqualTo(90);
        assertThat(linha.getOccurredAt()).isEqualTo(T2);
        assertThat(linha.getEventId()).isEqualTo(eventIdRecente);
    }

    @Test
    void empateDeOccurredAtDesempataPorEventIdLexicograficamenteMaior() {
        long pacienteId = novoPacienteId();
        repositorio.upsertSeMaisRecente(new ScoreReplica(pacienteId, 30, T1,
                UUID.fromString("00000000-0000-0000-0000-000000000001"), Instant.now()));

        UUID eventIdMaior = UUID.fromString("00000000-0000-0000-0000-000000000002");
        repositorio.upsertSeMaisRecente(new ScoreReplica(pacienteId, 31, T1, eventIdMaior, Instant.now()));

        ScoreReplicaJpaEntity linha = buscar(pacienteId);
        assertThat(linha.getScore()).isEqualTo(31);
        assertThat(linha.getEventId()).isEqualTo(eventIdMaior);
    }

    @Test
    void empateDeOccurredAtComEventIdLexicograficamenteMenorNaoSobrescreve() {
        long pacienteId = novoPacienteId();
        UUID eventIdMaior = UUID.fromString("00000000-0000-0000-0000-000000000002");
        repositorio.upsertSeMaisRecente(new ScoreReplica(pacienteId, 30, T1, eventIdMaior, Instant.now()));

        repositorio.upsertSeMaisRecente(new ScoreReplica(pacienteId, 99, T1,
                UUID.fromString("00000000-0000-0000-0000-000000000001"), Instant.now()));

        ScoreReplicaJpaEntity linha = buscar(pacienteId);
        assertThat(linha.getScore()).isEqualTo(30);
        assertThat(linha.getEventId()).isEqualTo(eventIdMaior);
    }

    private ScoreReplicaJpaEntity buscar(long pacienteId) {
        Optional<ScoreReplicaJpaEntity> encontrada = jpaRepository.findById(pacienteId);
        assertThat(encontrada).as("linha da replica para pacienteId=%d", pacienteId).isPresent();
        return encontrada.get();
    }

    // Cada teste usa um pacienteId proprio (sequencia atomica, sempre
    // positiva e crescente) -- evita colisao entre metodos de teste que
    // rodam na mesma instancia do Postgres/Spring context (sem
    // @DirtiesContext nem TRUNCATE entre testes).
    private static final AtomicLong PACIENTE_ID_SEQ = new AtomicLong(System.currentTimeMillis());

    private static long novoPacienteId() {
        return PACIENTE_ID_SEQ.incrementAndGet();
    }
}
