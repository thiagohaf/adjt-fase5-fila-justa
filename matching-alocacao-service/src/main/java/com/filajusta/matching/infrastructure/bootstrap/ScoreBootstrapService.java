package com.filajusta.matching.infrastructure.bootstrap;

import com.filajusta.matching.application.command.AtualizarScoreReplica;
import com.filajusta.matching.application.query.ScoreBootstrap;
import com.filajusta.matching.application.query.ScoreBootstrapIndisponivelException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Implementa {@link ScoreBootstrap} (Story 3.1c): chama
 * {@link TriagemScoreClient#buscarScores()} e upserta cada linha via
 * {@link AtualizarScoreReplica} (Story 3.1b) -- mesmo mecanismo já usado
 * por {@code ScoreCalculadoConsumerJob} (infrastructure/relay) para o
 * consumo em tempo real, sem introduzir uma segunda via de escrita para a
 * réplica (Design Notes da spec 3.1c: "Bootstrap reaproveita
 * upsertSeMaisRecente da 3.1b sem nova lógica de convergência, mesmo com
 * múltiplas linhas por paciente").
 *
 * <p>{@code @Transactional} no método inteiro (achado do code review --
 * Patch 1: falha parcial deixava a réplica permanentemente incompleta).
 * {@code ScoreReplicaRepositorioAdapter#upsertSeMaisRecente}
 * (infrastructure/persistence) já é {@code @Transactional} por chamada,
 * propagação {@code REQUIRED} (padrão do Spring) -- sem uma transação
 * externa aqui, cada upsert do loop abria/commitava a SUA PRÓPRIA
 * transação isoladamente. Se uma linha no meio do lote falhasse (ex.:
 * {@link com.filajusta.matching.domain.ScoreReplica} rejeita um score fora
 * de 0..100), as linhas anteriores já upsertadas ficavam commitadas: a
 * réplica deixava de estar vazia (
 * {@code FilaRepositorioAdapter#estaVazia()} só olha {@code count() == 0})
 * e todo {@code GET /v1/fila} seguinte pulava o bootstrap para sempre,
 * servindo uma fila PERMANENTEMENTE incompleta como se fosse completa --
 * exatamente a "fila incompleta silenciosa" que as Boundaries congeladas
 * da spec proíbem. Com a transação envolvendo o método inteiro, cada
 * {@code upsertSeMaisRecente} apenas ADERE a ela (mesma transação física,
 * em vez de abrir uma nova) -- uma falha em qualquer linha reverte TODO o
 * lote já upsertado nesta execução, a réplica volta a ficar vazia, e o
 * próximo {@code GET /v1/fila} reexecuta o bootstrap do zero.
 *
 * <p>Só a falha da chamada HTTP ({@link RestClientException}, lançada por
 * {@link TriagemScoreClient#buscarScores()}) vira
 * {@link ScoreBootstrapIndisponivelException} -&gt; {@code 503} (achado do
 * code review -- Patch 3): uma falha durante o upsert de uma linha (bug de
 * mapeamento ou de validação de domínio, ex.: score fora de 0..100) NÃO é
 * "triagem-score-service indisponível" -- propaga sem ser envolvida
 * (revertendo a transação do método, ver acima) e cai no fallback
 * genérico de {@code 500} de {@code FilaExceptionHandler}, mais honesto do
 * que confundir bug de dados com falha de infraestrutura externa.
 */
@Component
class ScoreBootstrapService implements ScoreBootstrap {

    private final TriagemScoreClient triagemScoreClient;
    private final AtualizarScoreReplica atualizarScoreReplica;

    ScoreBootstrapService(TriagemScoreClient triagemScoreClient, AtualizarScoreReplica atualizarScoreReplica) {
        this.triagemScoreClient = triagemScoreClient;
        this.atualizarScoreReplica = atualizarScoreReplica;
    }

    @Override
    @Transactional
    public void bootstrapar() {
        List<ScoreInternalDto> scores;
        try {
            scores = triagemScoreClient.buscarScores();
        } catch (RestClientException e) {
            throw new ScoreBootstrapIndisponivelException(
                    "Falha ao chamar GET /internal/scores (triagem-score-service indisponivel ou com erro)", e);
        }

        // Sem catch aqui de proposito (Patch 3): uma falha de upsert (ex.:
        // ScoreReplica rejeita um score invalido) propaga crua, reverte a
        // transacao do metodo (Patch 1, ver javadoc da classe) e vira 500
        // generico, nunca 503.
        for (ScoreInternalDto dto : scores) {
            atualizarScoreReplica.atualizar(dto.pacienteId(), dto.score().valor(), dto.occurredAt(), dto.eventId(),
                    dto.numeroSequencialTriagem());
        }
    }
}
