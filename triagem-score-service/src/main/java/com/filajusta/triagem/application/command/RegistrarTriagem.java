package com.filajusta.triagem.application.command;

import com.filajusta.triagem.domain.CalculadorDeScore;
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
 */
public class RegistrarTriagem {

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
                              List<String> sintomas) {
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
                UUID.randomUUID(), "ScoreCalculado", agora, payloadScoreCalculado(triagemSalva));
        eventoOutboxRepositorio.salvar(evento);

        return triagemSalva;
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
