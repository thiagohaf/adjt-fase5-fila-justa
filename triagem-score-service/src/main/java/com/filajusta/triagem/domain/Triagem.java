package com.filajusta.triagem.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Registro de Triagem (FR-1): dados clinicos de um {@link Paciente} e o
 * {@link Score} calculado a partir deles. {@code id} e {@code null} antes da
 * primeira persistencia.
 */
public final class Triagem {

    private final Long id;
    private final Long pacienteId;
    private final SinaisVitais sinaisVitais;
    private final GravidadePercebida gravidadePercebida;
    private final List<String> sintomas;
    private final Score score;
    private final Instant criadoEm;

    public Triagem(Long id,
                    Long pacienteId,
                    SinaisVitais sinaisVitais,
                    GravidadePercebida gravidadePercebida,
                    List<String> sintomas,
                    Score score,
                    Instant criadoEm) {
        this.id = id;
        this.pacienteId = Objects.requireNonNull(pacienteId, "pacienteId");
        this.sinaisVitais = Objects.requireNonNull(sinaisVitais, "sinaisVitais");
        this.gravidadePercebida = Objects.requireNonNull(gravidadePercebida, "gravidadePercebida");
        this.sintomas = List.copyOf(sintomasValidos(sintomas));
        this.score = Objects.requireNonNull(score, "score");
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm");
    }

    private static List<String> sintomasValidos(List<String> sintomas) {
        if (sintomas == null) {
            return List.of();
        }
        // List.copyOf lanca NPE (500 generico) se algum elemento for nulo --
        // validado aqui antes para virar 400 nomeando o campo "sintomas",
        // mesmo padrao de SinalVitalInvalidoException para outros campos.
        //
        // Iteracao explicita, nao ".contains(null)": List.of(...)/List.copyOf
        // (java.util.ImmutableCollections) tem contains/indexOf
        // null-hostil -- lanca NPE so por CHAMAR contains(null), mesmo sem
        // nenhum elemento nulo na lista. comId(...) reconstroi a Triagem
        // reaproveitando o {@code sintomas} ja imutavel, entao esse caminho
        // e sempre exercitado.
        for (String sintoma : sintomas) {
            if (sintoma == null) {
                throw new SinalVitalInvalidoException("sintomas", "sintomas nao pode conter elementos nulos");
            }
        }
        return sintomas;
    }

    /** Retorna uma copia desta Triagem com {@code id} preenchido (pos-persistencia). */
    public Triagem comId(Long idPersistido) {
        return new Triagem(idPersistido, pacienteId, sinaisVitais, gravidadePercebida, sintomas, score, criadoEm);
    }

    public Long getId() {
        return id;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public SinaisVitais getSinaisVitais() {
        return sinaisVitais;
    }

    public GravidadePercebida getGravidadePercebida() {
        return gravidadePercebida;
    }

    public List<String> getSintomas() {
        return sintomas;
    }

    public Score getScore() {
        return score;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
