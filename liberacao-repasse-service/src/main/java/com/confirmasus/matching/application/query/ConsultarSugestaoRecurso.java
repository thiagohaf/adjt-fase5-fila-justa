package com.confirmasus.matching.application.query;

import com.confirmasus.matching.application.command.EventoOutboxRepositorio;
import com.confirmasus.matching.application.command.UltimaSugestaoRegistradaRepositorio;
import com.confirmasus.matching.domain.EventoOutbox;
import com.confirmasus.matching.domain.ListaEsperaEntrada;
import com.confirmasus.matching.domain.Recurso;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Caso de uso de {@code GET /v1/recursos/{id}/sugestao}: indica qual
 * Paciente da Lista de Espera do Recurso consultado deveria ser sugerido
 * para Repasse. Ordem exclusivamente FIFO por {@code criadoEm} (AD-6,
 * Architecture Spine) -- nunca por gravidade, score ou qualquer critério
 * clínico. Sem alocação/reserva, sempre recalculado nesta consulta (sem
 * cache).
 *
 * <p>Exclui da Lista de Espera todo {@code pacienteId} com {@code Alocacao}
 * ATIVA ({@link com.confirmasus.matching.application.query.AlocacaoConsultaRepositorio#pacientesComAlocacaoAtiva()}) --
 * um Paciente já alocado a um Recurso não deve mais ser sugerido para
 * nenhum outro. Também pula candidatos já recusados especificamente para
 * este {@code recursoId} ({@link SugestaoRecusadaConsultaRepositorio#recusadosPara}),
 * avançando na fila até achar o primeiro Paciente elegível.
 *
 * <p>Quando a Lista de Espera se esgota antes de achar um Paciente elegível
 * (considerando exclusões e recusas), OU quando o próprio Recurso consultado
 * está {@code disponivel=false} (nunca é elegível), não há sugestão --
 * {@link Resultado#pacienteId()} vem {@code null}, nunca um erro.
 *
 * <p>{@link RecursoNaoEncontradoException} propaga sem tratamento -- não é
 * capturada aqui de propósito, para chegar até {@code infrastructure/web} e
 * virar {@code 404} RFC 7807.
 *
 * <p>A cada consulta, quando o {@code pacienteIdSugerido} calculado difere
 * do último registrado em {@link UltimaSugestaoRegistradaRepositorio},
 * grava o novo valor e publica {@code SugestaoGerada} via outbox -- mesmo
 * molde de {@code RecusarSugestao}/{@code ConfirmarAlocacao}. {@code
 * @Transactional} vive aqui, não em {@code domain/}, framework-agnóstico.
 *
 * <p>{@link UltimaSugestaoRegistradaRepositorio#registrar} é chamado direto,
 * sem pré-ler {@code pacienteIdRegistrado} antes: é um compare-and-set
 * atômico no próprio SQL (upsert nativo com {@code WHERE paciente_id <>
 * excluded.paciente_id}), fechando corrida de escrita concorrente entre
 * requisições simultâneas que calculam a mesma nova sugestão. Só quando
 * {@code registrar} retorna {@code true} (linha realmente
 * inserida/alterada) é que {@code SugestaoGerada} é publicado -- quando
 * {@code pacienteIdSugerido} é {@code null} (Lista de Espera esgotada/Recurso
 * indisponível), nenhuma chamada a {@code registrar} nem evento, mesmo
 * havendo um registro anterior diferente.
 */
public class ConsultarSugestaoRecurso {

    private static final int VERSAO_INICIAL_EVENTO = 1;

    private final RecursoConsultaRepositorio recursoConsultaRepositorio;
    private final ListaEsperaEntradaConsultaRepositorio listaEsperaEntradaConsultaRepositorio;
    private final AlocacaoConsultaRepositorio alocacaoConsultaRepositorio;
    private final SugestaoRecusadaConsultaRepositorio sugestaoRecusadaConsultaRepositorio;
    private final UltimaSugestaoRegistradaRepositorio ultimaSugestaoRegistradaRepositorio;
    private final EventoOutboxRepositorio eventoOutboxRepositorio;
    private final Clock clock;

    public ConsultarSugestaoRecurso(RecursoConsultaRepositorio recursoConsultaRepositorio,
                                     ListaEsperaEntradaConsultaRepositorio listaEsperaEntradaConsultaRepositorio,
                                     AlocacaoConsultaRepositorio alocacaoConsultaRepositorio,
                                     SugestaoRecusadaConsultaRepositorio sugestaoRecusadaConsultaRepositorio,
                                     UltimaSugestaoRegistradaRepositorio ultimaSugestaoRegistradaRepositorio,
                                     EventoOutboxRepositorio eventoOutboxRepositorio,
                                     Clock clock) {
        this.recursoConsultaRepositorio = recursoConsultaRepositorio;
        this.listaEsperaEntradaConsultaRepositorio = listaEsperaEntradaConsultaRepositorio;
        this.alocacaoConsultaRepositorio = alocacaoConsultaRepositorio;
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

        List<ListaEsperaEntrada> filaFifo =
                listaEsperaEntradaConsultaRepositorio.listarPorRecursoOrdenadoPorCriadoEm(recursoId);

        Set<Long> pacientesComAlocacaoAtiva = alocacaoConsultaRepositorio.pacientesComAlocacaoAtiva();
        Set<Long> recusados = sugestaoRecusadaConsultaRepositorio.recusadosPara(recursoId);

        Long pacienteIdSugerido = null;
        for (ListaEsperaEntrada entrada : filaFifo) {
            Long candidato = entrada.getPacienteId();
            if (!pacientesComAlocacaoAtiva.contains(candidato) && !recusados.contains(candidato)) {
                pacienteIdSugerido = candidato;
                break;
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
     * {@code pacienteId}: {@code null} quando a Lista de Espera se esgota
     * sem candidato elegível, ou quando o Recurso consultado está {@code
     * disponivel=false} -- ambos "sem Paciente elegível", nunca um erro.
     */
    public record Resultado(UUID recursoId, Long pacienteId) {
    }
}
