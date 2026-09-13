package com.filajusta.matching.application.query;

import com.filajusta.matching.application.command.EventoOutboxRepositorio;
import com.filajusta.matching.application.command.UltimaSugestaoRegistradaRepositorio;
import com.filajusta.matching.domain.EventoOutbox;
import com.filajusta.matching.domain.Recurso;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 *
 * <p>Desde a Story 3-3c2b2, {@code consultar()} também aplica o rastreamento
 * AD-10: quando o {@code pacienteIdSugerido} calculado difere do último
 * registrado em {@link UltimaSugestaoRegistradaRepositorio}, grava o novo
 * valor e publica {@code SugestaoGerada} via outbox -- mesmo molde de
 * {@code RecusarSugestao}/{@code ConfirmarAlocacao}. {@code
 * @Transactional} vive aqui, NÃO {@code readOnly}: propagação {@code
 * REQUIRED} precisa aceitar a escrita de bootstrap de {@link
 * ConsultarFilaPriorizada#consultar()} ({@code
 * ScoreReplicaRepositorioAdapter#upsertSeMaisRecente}, {@code @Transactional}
 * simples) quando ela participa desta mesma transação -- {@code
 * readOnly=true} faria o Postgres rejeitar essa escrita (mesmo risco
 * documentado em {@code FilaRepositorioAdapter}).
 *
 * <p>{@link UltimaSugestaoRegistradaRepositorio#registrar} é chamado direto,
 * SEM pré-ler {@code pacienteIdRegistrado} antes: desde a Story 3-3c2b2,
 * {@code registrar} é um compare-and-set atômico no próprio SQL (upsert
 * nativo com {@code WHERE paciente_id <> excluded.paciente_id}), fechando a
 * corrida de escrita concorrente identificada em revisão de código: 2
 * requisições simultâneas que calculam a mesma nova sugestão liam o mesmo
 * valor antigo e publicavam 2 eventos duplicados antes desta correção --
 * comportamento sob concorrência real (não só chamadas sequenciais)
 * verificado em {@code
 * UltimaSugestaoRegistradaRepositorioAdapterIntegrationTest#registrosConcorrentesParaOMesmoValorNovoApenasUmDelesRetornaTrue}.
 * Só quando {@code registrar} retorna {@code true} (linha realmente
 * inserida/alterada) é que {@code SugestaoGerada} é publicado -- quando
 * {@code pacienteIdSugerido} é {@code null} (fila esgotada/Recurso
 * indisponível), nenhuma chamada a {@code registrar} nem evento, mesmo
 * havendo um registro anterior diferente.
 */
public class ConsultarSugestaoRecurso {

    private static final int VERSAO_INICIAL_EVENTO = 1;

    private final RecursoConsultaRepositorio recursoConsultaRepositorio;
    private final ConsultarFilaPriorizada consultarFilaPriorizada;
    private final SugestaoRecusadaConsultaRepositorio sugestaoRecusadaConsultaRepositorio;
    private final UltimaSugestaoRegistradaRepositorio ultimaSugestaoRegistradaRepositorio;
    private final EventoOutboxRepositorio eventoOutboxRepositorio;
    private final Clock clock;

    public ConsultarSugestaoRecurso(RecursoConsultaRepositorio recursoConsultaRepositorio,
                                     ConsultarFilaPriorizada consultarFilaPriorizada,
                                     SugestaoRecusadaConsultaRepositorio sugestaoRecusadaConsultaRepositorio,
                                     UltimaSugestaoRegistradaRepositorio ultimaSugestaoRegistradaRepositorio,
                                     EventoOutboxRepositorio eventoOutboxRepositorio,
                                     Clock clock) {
        this.recursoConsultaRepositorio = recursoConsultaRepositorio;
        this.consultarFilaPriorizada = consultarFilaPriorizada;
        this.sugestaoRecusadaConsultaRepositorio = sugestaoRecusadaConsultaRepositorio;
        this.ultimaSugestaoRegistradaRepositorio = ultimaSugestaoRegistradaRepositorio;
        this.eventoOutboxRepositorio = eventoOutboxRepositorio;
        this.clock = clock;
    }

    @Transactional
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

        if (pacienteIdSugerido != null) {
            registrarERastrear(recursoId, pacienteIdSugerido);
        }

        return new Resultado(recursoId, pacienteIdSugerido);
    }

    private void registrarERastrear(UUID recursoId, long pacienteIdSugerido) {
        Instant agora = clock.instant();

        boolean mudou = ultimaSugestaoRegistradaRepositorio.registrar(recursoId, pacienteIdSugerido, agora);

        if (mudou) {
            EventoOutbox evento = new EventoOutbox(
                    null, UUID.randomUUID(), "SugestaoGerada", agora, VERSAO_INICIAL_EVENTO,
                    UUID.randomUUID().toString(), payloadSugestaoGerada(recursoId, pacienteIdSugerido, agora));
            eventoOutboxRepositorio.salvar(evento);
        }
    }

    private static Map<String, Object> payloadSugestaoGerada(UUID recursoId, long pacienteId, Instant sugeridoEm) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recursoId", recursoId);
        payload.put("pacienteId", pacienteId);
        payload.put("sugeridoEm", sugeridoEm);
        return payload;
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
