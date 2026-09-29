package com.confirmasus.matching.infrastructure.persistence;

import com.confirmasus.matching.application.command.RecursoRepositorio;
import com.confirmasus.matching.application.query.RecursoConsultaRepositorio;
import com.confirmasus.matching.domain.Recurso;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova, contra um Postgres 18 real via Testcontainers (sem LocalStack --
 * este teste não envolve SQS), que {@link RecursoConsultaRepositorio#buscarPorId}
 * devolve o {@link Recurso} persistido, ou vazio quando o {@code recursoId}
 * não existe.
 */
@Testcontainers
@SpringBootTest
// Relay SQS e relay outbox desligados -- este teste so cobre persistencia,
// sem depender de LocalStack/SQS/SNS.
@TestPropertySource(properties = {
        "confirmasus.matching.relay.enabled=false",
        "confirmasus.matching.outbox-relay.enabled=false",
        "confirmasus.matching.liberacao-agendada-relay.enabled=false"
})
class RecursoConsultaRepositorioAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private RecursoRepositorio recursoRepositorio;

    @Autowired
    private RecursoConsultaRepositorio recursoConsultaRepositorio;

    @Test
    void buscarPorIdDevolveORecursoPersistidoEVazioQuandoORecursoIdNaoExiste() {
        Recurso persistido = recursoRepositorio.upsert(
                new Recurso(UUID.randomUUID(), novoCodigoRecurso(), 1, true, null, null));

        Optional<Recurso> encontrado = recursoConsultaRepositorio.buscarPorId(persistido.getRecursoId());

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getRecursoId()).isEqualTo(persistido.getRecursoId());
        assertThat(encontrado.get().getCodigoRecurso()).isEqualTo(persistido.getCodigoRecurso());
        assertThat(recursoConsultaRepositorio.buscarPorId(UUID.randomUUID())).isEmpty();
    }

    // codigoRecurso proprio por teste (UNIQUE no banco).
    private static String novoCodigoRecurso() {
        return "RECURSO-" + UUID.randomUUID();
    }
}
