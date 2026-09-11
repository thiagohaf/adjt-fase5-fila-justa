package com.filajusta.matching.infrastructure.persistence;

import com.filajusta.matching.application.command.RecursoRepositorio;
import com.filajusta.matching.domain.Recurso;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova, contra um Postgres 18 real via Testcontainers (sem LocalStack --
 * este teste não envolve SQS), que {@link RecursoJpaRepository#upsert}
 * (INSERT ... ON CONFLICT (codigo_recurso) DO UPDATE) cria uma linha nova
 * quando {@code codigoRecurso} é inédito e atualiza {@code
 * especificidadeRank}/{@code disponivel} preservando o {@code recursoId}
 * quando já cadastrado (Tasks da spec 3.2b2: "Teste de integração de
 * persistência ... cobrindo insert e update").
 */
@Testcontainers
@SpringBootTest
@TestPropertySource(properties = "filajusta.matching.relay.enabled=false")
class RecursoRepositorioAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private RecursoRepositorio repositorio;

    @Test
    void primeiroUpsertDeUmCodigoRecursoIneditoInsereALinhaComORecursoIdDoCandidato() {
        String codigoRecurso = novoCodigoRecurso();
        UUID recursoIdCandidato = UUID.randomUUID();

        Recurso persistido = repositorio.upsert(new Recurso(recursoIdCandidato, codigoRecurso, 1, true));

        assertThat(persistido.getRecursoId()).isEqualTo(recursoIdCandidato);
        assertThat(persistido.getCodigoRecurso()).isEqualTo(codigoRecurso);
        assertThat(persistido.getEspecificidadeRank()).isEqualTo(1);
        assertThat(persistido.isDisponivel()).isTrue();
    }

    @Test
    void upsertDeCodigoRecursoJaCadastradoAtualizaOsCamposEPreservaORecursoIdOriginal() {
        String codigoRecurso = novoCodigoRecurso();
        UUID recursoIdOriginal = UUID.randomUUID();
        repositorio.upsert(new Recurso(recursoIdOriginal, codigoRecurso, 1, true));

        UUID recursoIdCandidatoDescartado = UUID.randomUUID();
        Recurso persistido = repositorio.upsert(
                new Recurso(recursoIdCandidatoDescartado, codigoRecurso, 4, false));

        assertThat(persistido.getRecursoId())
                .as("recurso_id deve permanecer o da primeira insercao, mesmo com um candidato novo")
                .isEqualTo(recursoIdOriginal)
                .isNotEqualTo(recursoIdCandidatoDescartado);
        assertThat(persistido.getEspecificidadeRank()).isEqualTo(4);
        assertThat(persistido.isDisponivel()).isFalse();
    }

    @Test
    void upsertsRepetidosComOsMesmosValoresSaoIdempotentesENaoDuplicamALinha() {
        String codigoRecurso = novoCodigoRecurso();
        UUID recursoId = UUID.randomUUID();

        Recurso primeiro = repositorio.upsert(new Recurso(recursoId, codigoRecurso, 2, true));
        Recurso segundo = repositorio.upsert(new Recurso(UUID.randomUUID(), codigoRecurso, 2, true));

        assertThat(segundo.getRecursoId()).isEqualTo(primeiro.getRecursoId());
        assertThat(segundo.getEspecificidadeRank()).isEqualTo(2);
        assertThat(segundo.isDisponivel()).isTrue();
    }

    // codigoRecurso proprio por teste (UNIQUE no banco) -- evita colisao
    // entre metodos de teste que rodam na mesma instancia do
    // Postgres/Spring context.
    private static String novoCodigoRecurso() {
        return "RECURSO-" + UUID.randomUUID();
    }
}
