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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
 * <p>Desde a Story 3-3c2b2, também cobre o retorno {@code boolean} do
 * compare-and-set atômico de {@link
 * UltimaSugestaoRegistradaRepositorio#registrar}: {@code pacienteId}
 * diferente do registrado -> {@code true} (linha alterada); {@code
 * pacienteId} igual ao já registrado -> {@code false} e {@code
 * registrado_em} inalterado (nenhuma escrita).
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
    void registrarDeNovoOMesmoRecursoComPacienteIdDiferenteAtualizaERetornaTrue() {
        UUID recursoId = UUID.randomUUID();
        ultimaSugestaoRegistradaRepositorio.registrar(recursoId, 42L, Instant.now());

        boolean alterou = ultimaSugestaoRegistradaRepositorio.registrar(recursoId, 99L, Instant.now());

        assertThat(alterou).isTrue();
        assertThat(ultimaSugestaoRegistradaRepositorio.pacienteIdRegistrado(recursoId))
                .contains(99L);
        assertThat(jpaRepository.count()).isEqualTo(1L);
    }

    @Test
    void registrarComOMesmoPacienteIdJaRegistradoRetornaFalseENaoAlteraRegistradoEm() {
        // Story 3-3c2b2: compare-and-set atomico no upsert nativo -- valor
        // repetido nao deve contar como linha afetada. Chamadas SEQUENCIAIS
        // (uma depois da outra, no mesmo thread) -- prova o comportamento do
        // WHERE condicional de forma deterministica, mas nao a atomicidade
        // sob concorrencia real; essa prova fica a cargo de
        // #registrosConcorrentesParaOMesmoValorNovoApenasUmDelesRetornaTrue
        // (N threads via ExecutorService/CountDownLatch), que fecha de fato a
        // corrida de 2 requisicoes concorrentes que publicariam
        // SugestaoGerada 2x.
        UUID recursoId = UUID.randomUUID();
        Instant primeiroRegistro = Instant.parse("2026-09-12T10:00:00Z");
        ultimaSugestaoRegistradaRepositorio.registrar(recursoId, 42L, primeiroRegistro);

        boolean alterou = ultimaSugestaoRegistradaRepositorio.registrar(
                recursoId, 42L, Instant.parse("2026-09-12T11:00:00Z"));

        assertThat(alterou).isFalse();
        assertThat(jpaRepository.findById(recursoId))
                .hasValueSatisfying(entidade -> {
                    assertThat(entidade.getPacienteId()).isEqualTo(42L);
                    assertThat(entidade.getRegistradoEm()).isEqualTo(primeiroRegistro);
                });
        assertThat(jpaRepository.count()).isEqualTo(1L);
    }

    @Test
    void registroDeUmRecursoNaoApareceParaOutroRecurso() {
        UUID recursoA = UUID.randomUUID();
        UUID recursoB = UUID.randomUUID();
        ultimaSugestaoRegistradaRepositorio.registrar(recursoA, 42L, Instant.now());

        assertThat(ultimaSugestaoRegistradaRepositorio.pacienteIdRegistrado(recursoB)).isEmpty();
    }

    @Test
    void registrosConcorrentesParaOMesmoValorNovoApenasUmDelesRetornaTrue() throws Exception {
        // Corrida real (nao sequencial) fechada pelo compare-and-set atomico
        // do upsert nativo (WHERE paciente_id <> excluded.paciente_id): N
        // threads concorrentes calculam a MESMA nova sugestao (pacienteNovo,
        // diferente do valor ja registrado) para o MESMO recursoId e
        // disparam registrar(...) juntas via latch -- mesmo padrao de
        // ConsultarFilaPriorizadaTest#requisicoesConcorrentesComReplicaVaziaDisparamBootstrapApenasUmaVez
        // (ExecutorService/CountDownLatch/Future), so que aqui contra um
        // Postgres real, nao um mock. Exatamente 1 chamada deve efetivamente
        // alterar a linha (true); as demais, ao rodar depois que a primeira
        // ja aplicou o novo valor, veem paciente_id ja igual a excluded e nao
        // escrevem (false). E essa unica chamada true que
        // ConsultarSugestaoRecurso usa como gatilho para publicar
        // SugestaoGerada -- sem essa garantia, 2+ requisicoes concorrentes
        // que calculam a mesma sugestao publicariam 2+ eventos duplicados.
        UUID recursoId = UUID.randomUUID();
        long pacienteAntigo = 1L;
        long pacienteNovo = 2L;
        ultimaSugestaoRegistradaRepositorio.registrar(recursoId, pacienteAntigo, Instant.now());

        int totalThreads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        try {
            CountDownLatch partida = new CountDownLatch(1);
            List<Future<Boolean>> resultados = new ArrayList<>();
            for (int i = 0; i < totalThreads; i++) {
                resultados.add(executor.submit(() -> {
                    partida.await();
                    return ultimaSugestaoRegistradaRepositorio.registrar(recursoId, pacienteNovo, Instant.now());
                }));
            }

            partida.countDown();
            long totalTrue = 0;
            for (Future<Boolean> resultado : resultados) {
                if (resultado.get(10, TimeUnit.SECONDS)) {
                    totalTrue++;
                }
            }

            assertThat(totalTrue)
                    .as("exatamente 1 das %d chamadas concorrentes deve alterar a linha -- fecha a duplicacao de "
                            + "eventos SugestaoGerada", totalThreads)
                    .isEqualTo(1L);
        } finally {
            executor.shutdown();
        }

        assertThat(ultimaSugestaoRegistradaRepositorio.pacienteIdRegistrado(recursoId)).contains(pacienteNovo);
        assertThat(jpaRepository.count()).isEqualTo(1L);
    }
}
