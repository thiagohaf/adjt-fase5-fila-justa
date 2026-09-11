package com.filajusta.matching.application.query;

import com.filajusta.matching.domain.Recurso;

import java.util.List;
import java.util.UUID;

/**
 * Caso de uso de {@code GET /v1/recursos/{id}/sugestao} (Story 3.2b3):
 * calcula qual Paciente da fila global deveria ser sugerido para um Recurso
 * específico, aplicando o algoritmo de tiers de desempate sobre a fila
 * priorizada já existente ({@link ConsultarFilaPriorizada#consultar()},
 * reutilizada sem duplicar o cálculo de Prioridade Efetiva/Aging --
 * Boundaries "Always" da spec 3.2b3).
 *
 * <p>Algoritmo: {@code N} é a quantidade de valores DISTINTOS de {@code
 * especificidadeRank} estritamente menores que o do Recurso consultado, com
 * pelo menos 1 Recurso {@code disponivel=true} naquele tier (calculado por
 * {@link RecursoConsultaRepositorio#contarTiersMaisGenericosDisponiveis},
 * SQL nativo -- Recursos do mesmo tier consomem 1 posição no total, nunca
 * uma por Recurso). A sugestão é {@code filaGlobal[N]} (índice 0) --
 * SEMPRE recalculada nesta consulta, sem cache nem reserva de Paciente
 * (Boundaries da spec 3.2b3).
 *
 * <p>Quando a fila global se esgota antes do índice {@code N} (menos
 * Pacientes na fila do que tiers mais genéricos disponíveis), OU quando o
 * próprio Recurso consultado está {@code disponivel=false} (nunca é
 * elegível, achado do code review multi-agente da Story 3.2b3 -- decisão do
 * usuário: mesmo tratamento de fila esgotada), não há sugestão -- {@link
 * Resultado#pacienteId()} vem {@code null}, NUNCA um erro (requisito do
 * epic, I/O Matrix "FILA_ESGOTADA"/"RECURSO_INDISPONIVEL" da spec 3.2b3).
 *
 * <p>{@link RecursoNaoEncontradoException} propaga sem tratamento -- não é
 * capturada aqui de propósito, para chegar até {@code infrastructure/web} e
 * virar {@code 404} RFC 7807 (mesmo padrão de {@code ConsultarTriagem},
 * triagem-score-service).
 */
public class ConsultarSugestaoRecurso {

    private final RecursoConsultaRepositorio recursoConsultaRepositorio;
    private final ConsultarFilaPriorizada consultarFilaPriorizada;

    public ConsultarSugestaoRecurso(RecursoConsultaRepositorio recursoConsultaRepositorio,
                                     ConsultarFilaPriorizada consultarFilaPriorizada) {
        this.recursoConsultaRepositorio = recursoConsultaRepositorio;
        this.consultarFilaPriorizada = consultarFilaPriorizada;
    }

    public Resultado consultar(UUID recursoId) {
        Recurso recurso = recursoConsultaRepositorio.buscarPorId(recursoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(recursoId));

        if (!recurso.isDisponivel()) {
            return new Resultado(recursoId, null);
        }

        int n = recursoConsultaRepositorio.contarTiersMaisGenericosDisponiveis(recurso.getEspecificidadeRank());

        List<ConsultarFilaPriorizada.ItemFila> filaGlobal = consultarFilaPriorizada.consultar();

        Long pacienteIdSugerido = n < filaGlobal.size() ? filaGlobal.get(n).pacienteId() : null;

        return new Resultado(recursoId, pacienteIdSugerido);
    }

    /**
     * {@code pacienteId}: {@code null} quando a fila global se esgota antes
     * do índice {@code N}, ou quando o Recurso consultado está {@code
     * disponivel=false} -- ambos "sem Paciente elegível" (I/O Matrix
     * "FILA_ESGOTADA"/"RECURSO_INDISPONIVEL" da spec 3.2b3), nunca um erro.
     */
    public record Resultado(UUID recursoId, Long pacienteId) {
    }
}
