package com.filajusta.agendamento.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado Agendamento (Story 1.1): associa um {@link Paciente} (resolvido
 * por CPF, {@code application.command.ResolverOuCriarPaciente}) a um
 * {@code recursoId} (validado so por formato -- AD-1, sem checagem de
 * existencia contra o catalogo real de Recurso) e uma
 * {@code dataHoraAgendamento} futura. Nasce sempre em
 * {@link StatusAgendamento#AGUARDANDO_JANELA}.
 *
 * <p>{@code janelaAbreEm} (Story 1.2, AD-4) e calculado e persistido no
 * momento do {@code RegistrarAgendamento} (spec 1.2, Design Notes) -- nunca
 * nulo. {@code janelaExpiraEm} ja existe como coluna (mesma migration) mas
 * so passa a ser preenchido pelo poller de expiracao da Story 1.5 (fora de
 * escopo desta spec) -- pode ser {@code null} ate la.
 *
 * <p>{@link #abrirJanela()} (imutavel, mesmo padrao de
 * {@code EventoOutbox.comId}) transiciona
 * {@link StatusAgendamento#AGUARDANDO_JANELA} para
 * {@link StatusAgendamento#AGUARDANDO_CONFIRMACAO} -- so representa a
 * transicao no dominio; a escrita condicional de fato
 * ({@code UPDATE ... WHERE status = 'AGUARDANDO_JANELA'}, AD-4) vive no
 * adapter ({@code AbrirJanelaDeConfirmacao}, application/command).
 *
 * <p>{@code id} e {@code null} antes da primeira persistencia (mesmo padrao
 * de {@link Paciente}).
 */
public final class Agendamento {

    private final Long id;
    private final Long pacienteId;
    private final UUID recursoId;
    private final Instant dataHoraAgendamento;
    private final StatusAgendamento status;
    private final Instant criadoEm;
    private final Instant janelaAbreEm;
    private final Instant janelaExpiraEm;
    private final String motivoLiberacao;

    public Agendamento(Long id, Long pacienteId, UUID recursoId, Instant dataHoraAgendamento,
                        StatusAgendamento status, Instant criadoEm, Instant janelaAbreEm, Instant janelaExpiraEm) {
        this(id, pacienteId, recursoId, dataHoraAgendamento, status, criadoEm, janelaAbreEm, janelaExpiraEm, null);
    }

    public Agendamento(Long id, Long pacienteId, UUID recursoId, Instant dataHoraAgendamento,
                        StatusAgendamento status, Instant criadoEm, Instant janelaAbreEm, Instant janelaExpiraEm,
                        String motivoLiberacao) {
        this.id = id;
        this.pacienteId = Objects.requireNonNull(pacienteId, "pacienteId");
        this.recursoId = Objects.requireNonNull(recursoId, "recursoId");
        this.dataHoraAgendamento = Objects.requireNonNull(dataHoraAgendamento, "dataHoraAgendamento");
        this.status = Objects.requireNonNull(status, "status");
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm");
        this.janelaAbreEm = Objects.requireNonNull(janelaAbreEm, "janelaAbreEm");
        this.janelaExpiraEm = janelaExpiraEm;
        this.motivoLiberacao = motivoLiberacao;
    }

    /**
     * Fabrica um novo Agendamento ainda nao persistido (Boundaries da spec
     * 1.1: estado inicial sempre {@code AGUARDANDO_JANELA}).
     * {@code janelaAbreEm} e recebido ja calculado pelo chamador
     * ({@code RegistrarAgendamento}, spec 1.2 -- duracao da janela e
     * {@code [ASSUMPTION]} configuravel, ver
     * {@code filajusta.agendamento.janela.duracao}). {@code janelaExpiraEm}
     * comeca sempre {@code null} (preenchido so pela Story 1.5).
     */
    public static Agendamento novo(Long pacienteId, UUID recursoId, Instant dataHoraAgendamento, Instant agora,
                                    Instant janelaAbreEm) {
        return new Agendamento(null, pacienteId, recursoId, dataHoraAgendamento,
                StatusAgendamento.AGUARDANDO_JANELA, agora, janelaAbreEm, null);
    }

    /**
     * Retorna uma copia deste Agendamento com {@code status} transicionado
     * para {@link StatusAgendamento#AGUARDANDO_CONFIRMACAO} (AD-4, spec 1.2)
     * -- mesmo padrao imutavel de {@code EventoOutbox.comId}.
     */
    public Agendamento abrirJanela() {
        return new Agendamento(id, pacienteId, recursoId, dataHoraAgendamento,
                StatusAgendamento.AGUARDANDO_CONFIRMACAO, criadoEm, janelaAbreEm, janelaExpiraEm);
    }

    /**
     * Retorna uma copia deste Agendamento com {@code status} transicionado
     * para {@link StatusAgendamento#CONFIRMADO} (spec 1.3) -- mesmo padrao
     * imutavel de {@link #abrirJanela()}. Assim como {@code abrirJanela()},
     * so representa a transicao no dominio; a escrita condicional de fato
     * ({@code UPDATE ... WHERE status = 'AGUARDANDO_CONFIRMACAO'}, AD-4) vive
     * no adapter, orquestrada por {@code ConfirmarPresenca}
     * (application/command) -- este metodo nao e chamado no caminho real de
     * persistencia (Boundaries da spec 1.3: "Nao reativar
     * Agendamento.abrirJanela() como padrao"), so existe para completude do
     * modelo de dominio.
     */
    public Agendamento confirmar() {
        return new Agendamento(id, pacienteId, recursoId, dataHoraAgendamento,
                StatusAgendamento.CONFIRMADO, criadoEm, janelaAbreEm, janelaExpiraEm, motivoLiberacao);
    }

    /**
     * Retorna uma copia deste Agendamento com {@code status} transicionado
     * para {@link StatusAgendamento#LIBERADO} e {@code motivoLiberacao = RECUSA}
     * (spec 1.4) -- mesmo padrao imutavel de {@link #abrirJanela()}.
     * So representa a transicao no dominio; a escrita condicional de fato
     * ({@code UPDATE ... WHERE status = 'AGUARDANDO_CONFIRMACAO'}, AD-4) vive
     * no adapter, orquestrada por {@code RecusarPresenca}
     * (application/command) -- este metodo nao e chamado no caminho real de
     * persistencia (Boundaries da spec 1.4: mesmo padrao que {@code confirmar()}),
     * so existe para completude do modelo de dominio.
     */
    public Agendamento recusar() {
        return new Agendamento(id, pacienteId, recursoId, dataHoraAgendamento,
                StatusAgendamento.LIBERADO, criadoEm, janelaAbreEm, janelaExpiraEm, MotivoLiberacao.RECUSA.name());
    }

    public Long getId() {
        return id;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public UUID getRecursoId() {
        return recursoId;
    }

    public Instant getDataHoraAgendamento() {
        return dataHoraAgendamento;
    }

    public StatusAgendamento getStatus() {
        return status;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getJanelaAbreEm() {
        return janelaAbreEm;
    }

    public Instant getJanelaExpiraEm() {
        return janelaExpiraEm;
    }

    public String getMotivoLiberacao() {
        return motivoLiberacao;
    }
}
