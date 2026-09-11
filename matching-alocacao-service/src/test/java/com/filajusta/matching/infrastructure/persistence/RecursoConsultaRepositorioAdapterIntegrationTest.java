package com.filajusta.matching.infrastructure.persistence;

import com.filajusta.matching.application.command.RecursoRepositorio;
import com.filajusta.matching.application.query.RecursoConsultaRepositorio;
import com.filajusta.matching.domain.Recurso;
import org.junit.jupiter.api.BeforeEach;
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
 * este teste não envolve SQS), que {@link
 * RecursoJpaRepository#contarTiersMaisGenericosDisponiveis} (Story 3.2b3,
 * {@code SELECT COUNT(DISTINCT especificidade_rank)} nativo) conta tiers
 * DISTINTOS -- Recursos do mesmo tier nunca somam mais de 1 posição --
 * ignora tiers sem nenhum Recurso disponível e tiers não estritamente
 * menores que o rank consultado (Tasks da spec 3.2b3: "Teste de integração
 * de persistência ... para a query de contagem de tiers", mesmo padrão de
 * {@link RecursoRepositorioAdapterIntegrationTest}).
 *
 * <p>Ao contrário dos demais testes de integração deste módulo (que isolam
 * cada método por uma chave própria -- {@code codigoRecurso}/{@code
 * pacienteId} único -- e dispensam limpeza entre testes, ver {@link
 * ScoreReplicaRepositorioAdapterIntegrationTest}), aqui isso não basta:
 * {@code contarTiersMaisGenericosDisponiveis} agrega TODAS as linhas da
 * tabela com {@code especificidade_rank < :rank}, então dados de um método
 * anterior (mesma instância de Postgres/Spring context, reusada entre
 * métodos desta classe) vazariam para a contagem do próximo mesmo usando
 * ranks "próprios" -- por isso {@link #limparRecursos()} trunca a tabela
 * antes de cada teste.
 */
@Testcontainers
@SpringBootTest
@TestPropertySource(properties = "filajusta.matching.relay.enabled=false")
class RecursoConsultaRepositorioAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private RecursoRepositorio recursoRepositorio;

    @Autowired
    private RecursoConsultaRepositorio recursoConsultaRepositorio;

    @Autowired
    private RecursoJpaRepository jpaRepository;

    @BeforeEach
    void limparRecursos() {
        jpaRepository.deleteAll();
    }

    @Test
    void buscarPorIdDevolveORecursoPersistidoEVazioQuandoORecursoIdNaoExiste() {
        Recurso persistido = recursoRepositorio.upsert(
                new Recurso(UUID.randomUUID(), novoCodigoRecurso(), 1, true));

        Optional<Recurso> encontrado = recursoConsultaRepositorio.buscarPorId(persistido.getRecursoId());

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getRecursoId()).isEqualTo(persistido.getRecursoId());
        assertThat(encontrado.get().getCodigoRecurso()).isEqualTo(persistido.getCodigoRecurso());
        assertThat(recursoConsultaRepositorio.buscarPorId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void doisRecursosDisponiveisNoMesmoTierContamComoUmUnicoTier() {
        // Boundaries "Always" da spec 3.2b3: Recursos do mesmo tier
        // consomem 1 posicao no total, nunca uma por Recurso.
        recursoRepositorio.upsert(new Recurso(UUID.randomUUID(), novoCodigoRecurso(), 1, true));
        recursoRepositorio.upsert(new Recurso(UUID.randomUUID(), novoCodigoRecurso(), 1, true));
        recursoRepositorio.upsert(new Recurso(UUID.randomUUID(), novoCodigoRecurso(), 2, true));

        int n = recursoConsultaRepositorio.contarTiersMaisGenericosDisponiveis(3);

        assertThat(n).isEqualTo(2);
    }

    @Test
    void tierIndisponivelNaoEContado() {
        recursoRepositorio.upsert(new Recurso(UUID.randomUUID(), novoCodigoRecurso(), 1, false));

        int n = recursoConsultaRepositorio.contarTiersMaisGenericosDisponiveis(2);

        assertThat(n).isZero();
    }

    @Test
    void tierIgualOuMaisEspecificoQueORankConsultadoNaoEContado() {
        recursoRepositorio.upsert(new Recurso(UUID.randomUUID(), novoCodigoRecurso(), 1, true));
        recursoRepositorio.upsert(new Recurso(UUID.randomUUID(), novoCodigoRecurso(), 2, true));

        // Consultando o proprio rank=1: nada estritamente menor existe --
        // N=0 (I/O Matrix "SEM_RECURSO_GENERICO_DISPONIVEL" da spec 3.2b3).
        int n = recursoConsultaRepositorio.contarTiersMaisGenericosDisponiveis(1);

        assertThat(n).isZero();
    }

    // codigoRecurso proprio por teste (UNIQUE no banco) -- mesmo padrao de
    // RecursoRepositorioAdapterIntegrationTest, mantido mesmo com o
    // deleteAll() em @BeforeEach (defensivo, sem custo).
    private static String novoCodigoRecurso() {
        return "RECURSO-" + UUID.randomUUID();
    }
}
