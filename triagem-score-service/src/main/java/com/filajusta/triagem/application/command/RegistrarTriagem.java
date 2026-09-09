package com.filajusta.triagem.application.command;

import com.filajusta.triagem.domain.CalculadorDeScore;
import com.filajusta.triagem.domain.CorrelationIdInvalidoException;
import com.filajusta.triagem.domain.Cpf;
import com.filajusta.triagem.domain.EventoOutbox;
import com.filajusta.triagem.domain.GravidadePercebida;
import com.filajusta.triagem.domain.LimitesSinaisVitais;
import com.filajusta.triagem.domain.Paciente;
import com.filajusta.triagem.domain.Score;
import com.filajusta.triagem.domain.SinaisVitais;
import com.filajusta.triagem.domain.Triagem;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Caso de uso de {@code POST /v1/triagens} (FR-1, FR-3): valida CPF e sinais
 * vitais (AD-11) -- {@link Cpf} e {@link SinaisVitais} lancam no primeiro
 * campo invalido, antes de qualquer calculo de Score --, resolve/cria o
 * Paciente (FR-2, idempotente), calcula o Score deterministico
 * ({@link CalculadorDeScore}, FR-4) e persiste Triagem + evento outbox
 * ({@code ScoreCalculado}, AD-3) na mesma transacao local (Boundaries da
 * spec 2.1: a resposta {@code 201} nunca depende de o evento ser lido).
 *
 * <p>{@code @Transactional} vive aqui (nao em {@code domain/}, que
 * permanece framework-agnostico por AD-2) porque este e o unico ponto que
 * precisa da atomicidade entre as 3 escritas.
 *
 * <p>{@code correlationId} (Story 3.0): propagado do header
 * {@code X-Correlation-Id} (lido por {@code TriagemController}, mesmo padrao
 * de {@code CorrelationIdFilter} do gateway-service) ou gerado localmente
 * (UUID v4) quando ausente/em branco -- nunca gravado nulo no outbox
 * (Boundaries da spec 3.0). {@code version} do envelope comeca em {@code 1}.
 * Validado ANTES de qualquer outro campo (achado do code review: um header
 * maior que {@value #CORRELATION_ID_MAX_LENGTH} caracteres quebraria no
 * {@code INSERT} de {@code eventos_outbox.correlation_id}
 * ({@code VARCHAR(128)}) como {@code 500} nao controlado -- agora vira
 * {@code 400} via {@link CorrelationIdInvalidoException}, mesma convencao
 * do servico de recusar cedo, antes de qualquer trabalho a jusante).
 */
public class RegistrarTriagem {

    // Versao inicial do envelope de evento fixado no Epic 2 (AD-3):
    // {eventId, eventType, occurredAt, version, correlationId, payload}.
    private static final int VERSAO_INICIAL_EVENTO = 1;

    // eventos_outbox.correlation_id e VARCHAR(128) (migration
    // V2__add_relay_columns_eventos_outbox.sql) -- limite persistivel.
    private static final int CORRELATION_ID_MAX_LENGTH = 128;

    private final ResolverOuCriarPaciente resolverOuCriarPaciente;
    private final TriagemRepositorio triagemRepositorio;
    private final EventoOutboxRepositorio eventoOutboxRepositorio;
    private final CalculadorDeScore calculadorDeScore;
    private final LimitesSinaisVitais limites;
    private final Clock clock;

    public RegistrarTriagem(ResolverOuCriarPaciente resolverOuCriarPaciente,
                             TriagemRepositorio triagemRepositorio,
                             EventoOutboxRepositorio eventoOutboxRepositorio,
                             CalculadorDeScore calculadorDeScore,
                             LimitesSinaisVitais limites,
                             Clock clock) {
        this.resolverOuCriarPaciente = resolverOuCriarPaciente;
        this.triagemRepositorio = triagemRepositorio;
        this.eventoOutboxRepositorio = eventoOutboxRepositorio;
        this.calculadorDeScore = calculadorDeScore;
        this.limites = limites;
        this.clock = clock;
    }

    @Transactional
    public Triagem registrar(String cpfTexto,
                              Double frequenciaCardiaca,
                              Double pressaoArterialSistolica,
                              Double pressaoArterialDiastolica,
                              Double saturacaoOxigenio,
                              Double frequenciaRespiratoria,
                              Double temperatura,
                              String gravidadePercebidaTexto,
                              List<String> sintomas,
                              String correlationId) {
        // Validado primeiro (achado do code review): e um campo de
        // transporte (header), independente do corpo, e precisa falhar
        // antes de qualquer trabalho a jusante (resolucao de Paciente,
        // calculo de Score, etc.), mesma logica da ordem de validacao do
        // corpo abaixo.
        String correlationIdEfetivo = correlationIdEfetivo(correlationId);

        // Ordem deterministica de validacao (Boundaries: 400 no primeiro
        // campo invalido, antes de qualquer calculo de Score): CPF, depois
        // sinais vitais (cada um lanca no primeiro campo ofensivo), depois
        // gravidade percebida.
        Cpf cpf = new Cpf(cpfTexto);
        SinaisVitais sinaisVitais = new SinaisVitais(
                frequenciaCardiaca, pressaoArterialSistolica, pressaoArterialDiastolica,
                saturacaoOxigenio, frequenciaRespiratoria, temperatura, limites);
        GravidadePercebida gravidadePercebida = GravidadePercebida.fromTexto(gravidadePercebidaTexto);

        Paciente paciente = resolverOuCriarPaciente.resolver(cpf);
        Score score = calculadorDeScore.calcular(sinaisVitais, gravidadePercebida, limites);

        Instant agora = clock.instant();
        Triagem triagemParaSalvar = new Triagem(
                null, paciente.getId(), sinaisVitais, gravidadePercebida, sintomas, score, agora);
        Triagem triagemSalva = triagemRepositorio.salvar(triagemParaSalvar);

        EventoOutbox evento = new EventoOutbox(
                null, UUID.randomUUID(), "ScoreCalculado", agora, VERSAO_INICIAL_EVENTO,
                correlationIdEfetivo, payloadScoreCalculado(triagemSalva));
        eventoOutboxRepositorio.salvar(evento);

        return triagemSalva;
    }

    // I/O Matrix da spec 3.0: "POST /v1/triagens sem header X-Correlation-Id"
    // -> correlationId gerado localmente (UUID v4), nunca gravado nulo.
    // Achado do code review: um correlationId explicito maior que o limite
    // persistivel (VARCHAR(128)) precisa virar 400, nao um 500 no INSERT.
    private static String correlationIdEfetivo(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        if (correlationId.length() > CORRELATION_ID_MAX_LENGTH) {
            throw new CorrelationIdInvalidoException(
                    "correlationId excede o limite de " + CORRELATION_ID_MAX_LENGTH + " caracteres");
        }
        return correlationId;
    }

    private static Map<String, Object> payloadScoreCalculado(Triagem triagem) {
        Score score = triagem.getScore();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pacienteId", triagem.getPacienteId());
        payload.put("triagemId", triagem.getId());
        payload.put("scoreValor", score.getValor());
        payload.put("algoritmoVersao", score.getAlgoritmoVersao());
        payload.put("fatores", score.getFatores());
        return payload;
    }
}
