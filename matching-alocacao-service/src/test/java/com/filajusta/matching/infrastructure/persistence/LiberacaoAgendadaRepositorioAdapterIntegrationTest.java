package com.filajusta.matching.infrastructure.persistence;

import com.filajusta.matching.application.command.LiberacaoAgendadaRepositorio;
import com.filajusta.matching.domain.LiberacaoAgendada;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova, contra um Postgres 18 real via Testcontainers, o comportamento de
 * {@link LiberacaoAgendadaRepositorioAdapter} (Story 3-4a1) --
 * {@code salvar}/{@code buscarPendentes}/{@code marcarComoEnviado} contra a
 * tabela real {@code matching_alocacao.liberacao_agendada}
 * ({@code V8__create_liberacao_agendada.sql}), mesmo padrão de
 * {@code AlocacaoRepositorioAdapterIntegrationTest}. {@code ConfirmarAlocacaoTest}
 * (unitário, com mocks) já prova a orquestração/cálculo do delay -- aqui só
 * se prova a persistência real.
 */
@Testcontainers
@SpringBootTest
@TestPropertySource(properties = {
        "filajusta.matching.relay.enabled=false",
        "filajusta.matching.outbox-relay.enabled=false"
})
class LiberacaoAgendadaRepositorioAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private LiberacaoAgendadaRepositorio repositorio;

    private static LiberacaoAgendada novaLiberacao(UUID alocacaoId, Instant criadoEm) {
        return new LiberacaoAgendada(alocacaoId, UUID.randomUUID(), "corr-" + alocacaoId, 120, criadoEm, null);
    }

    @Test
    void salvarPersisteELiberacaoApareceEmBuscarPendentes() {
        UUID alocacaoId = UUID.randomUUID();
        Instant criadoEm = Instant.now().truncatedTo(ChronoUnit.MICROS);

        repositorio.salvar(novaLiberacao(alocacaoId, criadoEm));

        List<LiberacaoAgendada> pendentes = repositorio.buscarPendentes(100);

        assertThat(pendentes).anySatisfy(liberacao -> {
            assertThat(liberacao.getAlocacaoId()).isEqualTo(alocacaoId);
            assertThat(liberacao.getDelaySegundos()).isEqualTo(120);
            assertThat(liberacao.getCriadoEm()).isEqualTo(criadoEm);
            assertThat(liberacao.getEnviadoEm()).isNull();
        });
    }

    @Test
    void buscarPendentesRespeitaOLimiteEDevolveAMaisAntigaPrimeiro() {
        // Achado do code review multi-agente da spec 3-4a1: o teste anterior
        // so verificava o TAMANHO da lista quando o limite corta o
        // resultado, nunca que a linha sobrevivente e a mais antiga (a query
        // nativa usa ORDER BY criado_em ASC) -- um bug que trocasse por
        // DESC, ou removesse o ORDER BY, passaria despercebido.
        //
        // Instant no passado distante para "maisAntiga" -- garante que esta
        // e a mais antiga entre QUALQUER linha pendente na tabela, mesmo as
        // deixadas por outros metodos de teste desta classe (estado nao
        // isolado, mesmo Postgres/Spring context compartilhado, mesmo
        // raciocinio de "pacienteId proprio por teste" em
        // AlocacaoRepositorioAdapterIntegrationTest).
        UUID maisAntiga = UUID.randomUUID();
        Instant criadoEmBase = Instant.parse("2000-01-01T00:00:00Z");
        repositorio.salvar(novaLiberacao(maisAntiga, criadoEmBase));
        repositorio.salvar(novaLiberacao(UUID.randomUUID(), criadoEmBase.plusSeconds(10)));
        repositorio.salvar(novaLiberacao(UUID.randomUUID(), criadoEmBase.plusSeconds(20)));

        List<LiberacaoAgendada> pendentes = repositorio.buscarPendentes(1);

        assertThat(pendentes).hasSize(1);
        assertThat(pendentes.get(0).getAlocacaoId()).isEqualTo(maisAntiga);
    }

    @Test
    void marcarComoEnviadoRemoveALinhaDosPendentesEDevolveTrueNaPrimeiraChamada() {
        UUID alocacaoId = UUID.randomUUID();
        repositorio.salvar(novaLiberacao(alocacaoId, Instant.now()));

        boolean marcada = repositorio.marcarComoEnviado(alocacaoId);

        assertThat(marcada).isTrue();
        assertThat(repositorio.buscarPendentes(100))
                .noneMatch(liberacao -> liberacao.getAlocacaoId().equals(alocacaoId));
    }

    @Test
    void marcarComoEnviadoDevolveFalseQuandoALinhaJaEstavaMarcada() {
        // Corrida entre instancias do futuro relay (Story 3-4a2, Boundaries
        // da spec 3-4a1) -- nao e um erro, mesmo raciocinio de
        // EventoOutboxRepositorio#marcarComoPublicado.
        UUID alocacaoId = UUID.randomUUID();
        repositorio.salvar(novaLiberacao(alocacaoId, Instant.now()));
        repositorio.marcarComoEnviado(alocacaoId);

        boolean marcadaDeNovo = repositorio.marcarComoEnviado(alocacaoId);

        assertThat(marcadaDeNovo).isFalse();
    }
}
