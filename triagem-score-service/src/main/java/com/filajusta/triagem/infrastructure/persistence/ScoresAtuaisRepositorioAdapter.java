package com.filajusta.triagem.infrastructure.persistence;

import com.filajusta.triagem.application.query.ScoreAtual;
import com.filajusta.triagem.application.query.ScoresAtuaisRepositorio;
import com.filajusta.triagem.domain.FatorContribuinte;
import com.filajusta.triagem.domain.Score;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adapter que implementa a porta {@link ScoresAtuaisRepositorio}
 * (application/query, Story 3.1a) lendo diretamente de {@code
 * triagem_score.eventos_outbox} via {@link EventoOutboxJpaRepository} --
 * unica tabela que carrega, na mesma linha, {@code event_id} e {@code
 * occurred_at} (colunas proprias do evento {@code ScoreCalculado}) junto de
 * {@code pacienteId} e do {@link Score} completo (dentro de {@code
 * payload}, jsonb). Ler de {@code triagens} exigiria um join com {@code
 * eventos_outbox} para obter {@code eventId} (Triagem/TriagemJpaEntity nao
 * tem essa coluna) -- Code Map da spec 3.1a pede reaproveitar o repositorio
 * JPA existente sem query nova complexa, entao o outbox e a fonte mais
 * simples que ja tem tudo numa linha so.
 *
 * <p>Desserializa {@code payload} como {@code Map} generico (mesmo padrao de
 * {@link EventoOutboxRepositorioAdapter#paraDominio}), depois converte os
 * campos necessarios -- {@code pacienteId}/{@code scoreValor} chegam como
 * {@link Number} (Integer ou Long dependendo do valor, desserializacao
 * generica do Jackson), nunca um cast direto para {@code Long}/{@code int}.
 *
 * <p><b>Resiliencia por linha (achado do code review):</b> {@link
 * #listarTodos()} isola a falha de UMA linha (JSON invalido, campo
 * ausente/de tipo errado, {@code scoreValor} fora de {@code 0..100}) em vez
 * de derrubar a resposta inteira de {@code GET /internal/scores} -- mesmo
 * padrao de {@code RelaySnsPublisherJob} (infrastructure/relay): loga o
 * {@code eventId} da linha problematica e a pula, sem interromper o
 * restante da listagem. Um unico registro malformado nao pode quebrar o
 * bootstrap a frio inteiro do {@code matching-alocacao-service}.
 */
@Component
class ScoresAtuaisRepositorioAdapter implements ScoresAtuaisRepositorio {

    private static final Logger log = LoggerFactory.getLogger(ScoresAtuaisRepositorioAdapter.class);

    // Mesmo eventType gravado por RegistrarTriagem (application/command) --
    // unico tipo de evento emitido nesta fase (AD-3). Filtro defensivo: se
    // um segundo eventType passar a existir nesta mesma tabela outbox, ele
    // nao deve aparecer como "Score atual".
    private static final String EVENT_TYPE_SCORE_CALCULADO = "ScoreCalculado";

    private final EventoOutboxJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;

    ScoresAtuaisRepositorioAdapter(EventoOutboxJpaRepository jpaRepository, ObjectMapper objectMapper) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ScoreAtual> listarTodos() {
        List<EventoOutboxJpaEntity> eventos =
                jpaRepository.findByEventTypeOrderByOccurredAtAsc(EVENT_TYPE_SCORE_CALCULADO);

        List<ScoreAtual> scoresAtuais = new ArrayList<>(eventos.size());
        for (EventoOutboxJpaEntity entity : eventos) {
            try {
                scoresAtuais.add(paraScoreAtual(entity));
            } catch (RuntimeException e) {
                // Isola por linha (achado do code review): payload
                // malformado/incompleto/de um schema futuro em UM evento
                // nao pode derrubar GET /internal/scores inteiro -- a linha
                // ruim e ignorada, logada com o eventId para investigacao.
                log.error("Falha ao reconstruir Score atual do evento {} (payload malformado/incompleto) "
                        + "-- linha ignorada nesta listagem", entity.getEventId(), e);
            }
        }
        return scoresAtuais;
    }

    @SuppressWarnings("unchecked")
    private ScoreAtual paraScoreAtual(EventoOutboxJpaEntity entity) {
        Map<String, Object> payload = objectMapper.readValue(entity.getPayload(), Map.class);

        Object pacienteIdBruto = payload.get("pacienteId");
        Object scoreValorBruto = payload.get("scoreValor");
        Object algoritmoVersaoBruto = payload.get("algoritmoVersao");
        Object fatoresBruto = payload.get("fatores");

        // Validado explicitamente (achado do code review) em vez de um cast
        // direto: um payload sem algum destes campos (ou com o tipo errado)
        // vira uma excecao com o eventId no texto, capturada por
        // listarTodos() -- nunca um NPE/ClassCastException cru propagando
        // ate o controller.
        if (!(pacienteIdBruto instanceof Number) || !(scoreValorBruto instanceof Number)
                || !(algoritmoVersaoBruto instanceof String) || fatoresBruto == null) {
            throw new IllegalStateException(
                    "payload do evento " + entity.getEventId() + " incompleto ou malformado -- "
                            + "esperado pacienteId (numero), scoreValor (numero), algoritmoVersao (texto) "
                            + "e fatores");
        }

        Long pacienteId = ((Number) pacienteIdBruto).longValue();
        int scoreValor = ((Number) scoreValorBruto).intValue();
        String algoritmoVersao = (String) algoritmoVersaoBruto;
        List<FatorContribuinte> fatores =
                List.of(objectMapper.convertValue(fatoresBruto, FatorContribuinte[].class));

        // Score(...) tambem pode lancar (ex.: scoreValor fora de 0..100) --
        // propaga como RuntimeException, capturada por listarTodos() como
        // qualquer outro payload malformado.
        Score score = new Score(scoreValor, algoritmoVersao, fatores);
        return new ScoreAtual(pacienteId, score, entity.getOccurredAt(), entity.getEventId());
    }
}
