package com.filajusta.matching.application.query;

import com.filajusta.matching.domain.Recurso;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Caso de uso de {@code GET /v1/recursos/{id}/sugestao} (Story 3.2b3):
 * calcula qual Paciente da fila global deveria ser sugerido para um Recurso
 * específico, aplicando o algoritmo de tiers de desempate sobre a fila
 * priorizada já existente ({@link ConsultarFilaPriorizada#consultar()},
 * reutilizada sem duplicar o cálculo de Prioridade Efetiva/Aging --
 * Boundaries "Always" da spec 3.2b3). Desde a Story 3-3b2b,
 * {@code consultar()} passou a excluir da fila global todo Paciente com
 * {@code Alocacao} ativa -- {@code ConsultarSugestaoRecurso} herda esse
 * filtro automaticamente por reutilizar o mesmo método, sem duplicar
 * lógica nem precisar de mudança própria.
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
 * <p>Desde a Story 3-3c2a, o candidato em {@code filaGlobal[N]} é pulado se
 * já foi recusado para este {@code recursoId} especificamente ({@link
 * SugestaoRecusadaConsultaRepositorio#recusadosPara}, tabela {@code
 * sugestao_recusada} da Story 3-3c1) -- a busca avança dentro de {@code
 * filaGlobal} a partir do índice {@code N} até achar o primeiro Paciente não
 * recusado; {@code N} em si não muda, então a contagem de tiers de outros
 * Recursos não é afetada.
 *
 * <p>Quando a fila global se esgota antes de achar um Paciente elegível
 * (considerando o pulo de recusados), OU quando o próprio Recurso consultado
 * está {@code disponivel=false} (nunca é elegível, achado do code review
 * multi-agente da Story 3.2b3 -- decisão do usuário: mesmo tratamento de
 * fila esgotada), não há sugestão -- {@link Resultado#pacienteId()} vem
 * {@code null}, NUNCA um erro (requisito do epic, I/O Matrix
 * "FILA_ESGOTADA"/"RECURSO_INDISPONIVEL" da spec 3.2b3).
 *
 * <p>{@link RecursoNaoEncontradoException} propaga sem tratamento -- não é
 * capturada aqui de propósito, para chegar até {@code infrastructure/web} e
 * virar {@code 404} RFC 7807 (mesmo padrão de {@code ConsultarTriagem},
 * triagem-score-service).
 */
public class ConsultarSugestaoRecurso {

    private final RecursoConsultaRepositorio recursoConsultaRepositorio;
    private final ConsultarFilaPriorizada consultarFilaPriorizada;
    private final SugestaoRecusadaConsultaRepositorio sugestaoRecusadaConsultaRepositorio;

    public ConsultarSugestaoRecurso(RecursoConsultaRepositorio recursoConsultaRepositorio,
                                     ConsultarFilaPriorizada consultarFilaPriorizada,
                                     SugestaoRecusadaConsultaRepositorio sugestaoRecusadaConsultaRepositorio) {
        this.recursoConsultaRepositorio = recursoConsultaRepositorio;
        this.consultarFilaPriorizada = consultarFilaPriorizada;
        this.sugestaoRecusadaConsultaRepositorio = sugestaoRecusadaConsultaRepositorio;
    }

    public Resultado consultar(UUID recursoId) {
        Recurso recurso = recursoConsultaRepositorio.buscarPorId(recursoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(recursoId));

        if (!recurso.isDisponivel()) {
            return new Resultado(recursoId, null);
        }

        int n = recursoConsultaRepositorio.contarTiersMaisGenericosDisponiveis(recurso.getEspecificidadeRank());

        List<ConsultarFilaPriorizada.ItemFila> filaGlobal = consultarFilaPriorizada.consultar();

        Long pacienteIdSugerido = null;
        if (n < filaGlobal.size()) {
            Set<Long> recusados = sugestaoRecusadaConsultaRepositorio.recusadosPara(recursoId);
            for (int i = n; i < filaGlobal.size(); i++) {
                long candidato = filaGlobal.get(i).pacienteId();
                if (!recusados.contains(candidato)) {
                    pacienteIdSugerido = candidato;
                    break;
                }
            }
        }

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
