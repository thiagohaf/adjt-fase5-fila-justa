package com.filajusta.matching.infrastructure.persistence;

import com.filajusta.matching.application.command.SugestaoRecusadaRepositorio;
import com.filajusta.matching.application.query.SugestaoRecusadaConsultaRepositorio;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova, contra um Postgres 18 real via Testcontainers, que {@link
 * SugestaoRecusadaJpaRepository#buscarPacientesRecusados} (Story 3-3c2a,
 * {@code SELECT paciente_id ... WHERE recurso_id = :recursoId} nativo)
 * devolve os {@code pacienteId} de todo par recusado persistido via {@link
 * SugestaoRecusadaRepositorio#registrar} -- I/O Matrix da spec 3-3c2a, mesmo
 * padrão de {@link AlocacaoConsultaRepositorioAdapterIntegrationTest}.
 *
 * <p>{@link #limparRecusas()} trunca a tabela antes de cada teste -- mesmo
 * padrão de {@link AlocacaoConsultaRepositorioAdapterIntegrationTest},
 * necessário porque {@code sugestao_recusada} não tem coluna própria de
 * isolamento por teste.
 */
@Testcontainers
@SpringBootTest
@TestPropertySource(properties = {
        "filajusta.matching.relay.enabled=false",
        "filajusta.matching.outbox-relay.enabled=false"
})
class SugestaoRecusadaConsultaRepositorioAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private SugestaoRecusadaRepositorio sugestaoRecusadaRepositorio;

    @Autowired
    private SugestaoRecusadaConsultaRepositorio sugestaoRecusadaConsultaRepositorio;

    @Autowired
    private SugestaoRecusadaJpaRepository jpaRepository;

    @BeforeEach
    void limparRecusas() {
        jpaRepository.deleteAll();
    }

    @Test
    void nenhumaRecusaParaORecursoDevolveSetVazio() {
        // Prova literal da linha "Nenhum recusado -> Set vazio" da I/O
        // Matrix da spec 3-3c2a -- isEmpty(), nao apenas doesNotContain(),
        // senao um bug que ignorasse o WHERE e devolvesse tudo ainda
        // passaria.
        Set<Long> recusados = sugestaoRecusadaConsultaRepositorio.recusadosPara(UUID.randomUUID());

        assertThat(recusados).isEmpty();
    }

    @Test
    void pacienteRecusadoApareceNoSet() {
        UUID recursoId = UUID.randomUUID();
        sugestaoRecusadaRepositorio.registrar(recursoId, 111L, "sem leitos", Instant.now());

        Set<Long> recusados = sugestaoRecusadaConsultaRepositorio.recusadosPara(recursoId);

        assertThat(recusados).containsExactly(111L);
    }

    @Test
    void multiplosPacientesRecusadosParaOMesmoRecursoAparecemTodosNoSet() {
        UUID recursoId = UUID.randomUUID();
        sugestaoRecusadaRepositorio.registrar(recursoId, 111L, "sem leitos", Instant.now());
        sugestaoRecusadaRepositorio.registrar(recursoId, 222L, "fora da especialidade", Instant.now());

        Set<Long> recusados = sugestaoRecusadaConsultaRepositorio.recusadosPara(recursoId);

        assertThat(recusados).containsExactlyInAnyOrder(111L, 222L);
    }

    @Test
    void recusaDeOutroRecursoNaoApareceNoSetDesteRecursoEONaoOWhereInteiro() {
        // Prova que o WHERE recurso_id = :recursoId realmente discrimina --
        // sem este teste, uma query sem WHERE nenhum (SELECT paciente_id
        // FROM sugestao_recusada) passaria igual nos demais testes desta
        // classe, que so consultam um unico recursoId por vez.
        UUID recursoConsultado = UUID.randomUUID();
        UUID outroRecurso = UUID.randomUUID();
        sugestaoRecusadaRepositorio.registrar(recursoConsultado, 111L, "sem leitos", Instant.now());
        sugestaoRecusadaRepositorio.registrar(outroRecurso, 999L, "sem leitos", Instant.now());

        Set<Long> recusados = sugestaoRecusadaConsultaRepositorio.recusadosPara(recursoConsultado);

        assertThat(recusados).containsExactly(111L);
        assertThat(recusados).doesNotContain(999L);
    }
}
