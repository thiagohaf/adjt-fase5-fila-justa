package com.confirmasus.triagem.infrastructure.web;

import com.confirmasus.triagem.infrastructure.persistence.TriagemJpaEntity;
import com.confirmasus.triagem.infrastructure.persistence.TriagemJpaRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * {@code GET /internal/scores} (Story 3.1a): endpoint que faltava neste
 * checkout para o bootstrap a frio da réplica de Score do
 * matching-alocacao-service ({@code ScoreBootstrapService}) -- sem ele,
 * {@code GET /v1/fila}/sugestão de repasse nunca conseguia popular a
 * réplica (vazia) e sempre respondia 503 ("Bootstrap da réplica de Score
 * indisponível").
 *
 * <p>Retorna uma linha por Triagem (não só a mais recente por paciente):
 * {@code AtualizarScoreReplica} do lado consumidor já é idempotente e só
 * aplica a atualização quando o {@code occurredAt} é mais recente que o
 * que já está na réplica, então múltiplas Triagens do mesmo paciente
 * convergem para o estado correto sem filtro extra aqui.
 */
@RestController
@RequestMapping("/internal/scores")
public class ScoreInternalController {

    private final TriagemJpaRepository triagemJpaRepository;

    public ScoreInternalController(TriagemJpaRepository triagemJpaRepository) {
        this.triagemJpaRepository = triagemJpaRepository;
    }

    @GetMapping
    public List<ScoreInternalResponse> listar() {
        return triagemJpaRepository.findAll().stream()
                .map(ScoreInternalController::paraResponse)
                .toList();
    }

    private static ScoreInternalResponse paraResponse(TriagemJpaEntity entity) {
        return new ScoreInternalResponse(
                derivarPacienteIdNumerico(entity.getPacienteId()),
                new ScoreInternalResponse.ScoreResponse(entity.getScoreValor()),
                entity.getRegistradoEm(),
                entity.getId(),
                null);
    }

    /**
     * Deriva um long positivo estável a partir do UUID do Paciente (ver
     * javadoc de {@link ScoreInternalResponse}) -- mesmo paciente sempre
     * produz o mesmo valor, mas o valor não tem relação com o pacienteId de
     * nenhum outro serviço. Mascara o bit de sinal para nunca ser negativo
     * e garante {@code > 0} (ScoreReplica rejeita zero/negativo).
     */
    static long derivarPacienteIdNumerico(UUID pacienteId) {
        long valor = pacienteId.getMostSignificantBits() & Long.MAX_VALUE;
        return valor == 0 ? 1 : valor;
    }
}
