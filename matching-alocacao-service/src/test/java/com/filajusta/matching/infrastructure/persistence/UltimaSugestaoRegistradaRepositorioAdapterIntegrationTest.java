package com.filajusta.matching.infrastructure.persistence;

import com.filajusta.matching.application.command.UltimaSugestaoRegistradaRepositorio;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova, contra um Postgres 18 real via Testcontainers, que {@link
 * UltimaSugestaoRegistradaRepositorioAdapter} cobre a I/O Matrix completa da
 * spec 3-3c2b1: nenhum registro -> {@code Optional.empty()}; primeiro
 * registro -> {@code Optional.of(pacienteId)}; registro repetido do mesmo
 * Recurso -> upsert idempotente (atualiza, nunca duplica); isolamento entre
 * Recursos -- mesmo padrão de {@link
 * SugestaoRecusadaConsultaRepositorioAdapterIntegrationTest}.
 *
 * <p>{@link #limparRegistros()} trunca a tabela antes de cada teste --
 * necessário porque {@code ultima_sugestao_registrada} não tem coluna
 * própria de isolamento por teste.
 */
@Testcontainers
@SpringBootTest
@TestPropertySource(properties = {
        "filajusta.matching.relay.enabled=false",
        "filajusta.matching.outbox-relay.enabled=false"
})
class UltimaSugestaoRegistradaRepositorioAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private UltimaSugestaoRegistradaRepositorio ultimaSugestaoRegistradaRepositorio;

    @Autowired
    private UltimaSugestaoRegistradaJpaRepository jpaRepository;

    @BeforeEach
    void limparRegistros() {
        jpaRepository.deleteAll();
    }

    @Test
    void nenhumRegistroParaORecursoDevolveOptionalVazio() {
        Optional<Long> pacienteId = ultimaSugestaoRegistradaRepositorio.pacienteIdRegistrado(UUID.randomUUID());

        assertThat(pacienteId).isEmpty();
    }

    @Test
    void primeiroRegistroPassaASerDevolvidoPelaLeitura() {
        UUID recursoId = UUID.randomUUID();

        ultimaSugestaoRegistradaRepositorio.registrar(recursoId, 42L, Instant.now());

        assertThat(ultimaSugestaoRegistradaRepositorio.pacienteIdRegistrado(recursoId))
                .contains(42L);
    }

    @Test
    void registrarDeNovoOMesmoRecursoAtualizaSemDuplicarNemFalhar() {
        UUID recursoId = UUID.randomUUID();
        ultimaSugestaoRegistradaRepositorio.registrar(recursoId, 42L, Instant.now());

        ultimaSugestaoRegistradaRepositorio.registrar(recursoId, 99L, Instant.now());

        assertThat(ultimaSugestaoRegistradaRepositorio.pacienteIdRegistrado(recursoId))
                .contains(99L);
        assertThat(jpaRepository.count()).isEqualTo(1L);
    }

    @Test
    void registroDeUmRecursoNaoApareceParaOutroRecurso() {
        UUID recursoA = UUID.randomUUID();
        UUID recursoB = UUID.randomUUID();
        ultimaSugestaoRegistradaRepositorio.registrar(recursoA, 42L, Instant.now());

        assertThat(ultimaSugestaoRegistradaRepositorio.pacienteIdRegistrado(recursoB)).isEmpty();
    }
}
