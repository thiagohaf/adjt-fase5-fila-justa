package com.filajusta.triagem.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Mapeamento JPA de {@code triagem_score.triagens} (schema proprio, AD-9).
 * {@code sintomas} e {@code score_fatores} sao colunas {@code jsonb} --
 * mapeadas como {@code String} (JSON ja serializado pelo adapter) via
 * {@code @JdbcTypeCode(SqlTypes.JSON)}, sem depender de um format mapper de
 * objeto automatico do Hibernate.
 */
@Entity
@Table(name = "triagens", schema = "triagem_score")
public class TriagemJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(name = "frequencia_cardiaca", nullable = false)
    private Double frequenciaCardiaca;

    @Column(name = "pressao_arterial_sistolica", nullable = false)
    private Double pressaoArterialSistolica;

    @Column(name = "pressao_arterial_diastolica", nullable = false)
    private Double pressaoArterialDiastolica;

    @Column(name = "saturacao_oxigenio", nullable = false)
    private Double saturacaoOxigenio;

    @Column(name = "frequencia_respiratoria", nullable = false)
    private Double frequenciaRespiratoria;

    @Column(nullable = false)
    private Double temperatura;

    @Column(name = "gravidade_percebida", nullable = false)
    private String gravidadePercebida;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String sintomas;

    @Column(name = "score_valor", nullable = false)
    private Integer scoreValor;

    @Column(name = "score_algoritmo_versao", nullable = false)
    private String scoreAlgoritmoVersao;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_fatores", nullable = false, columnDefinition = "jsonb")
    private String scoreFatores;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected TriagemJpaEntity() {
        // Exigido pelo JPA.
    }

    public TriagemJpaEntity(Long pacienteId,
                             Double frequenciaCardiaca,
                             Double pressaoArterialSistolica,
                             Double pressaoArterialDiastolica,
                             Double saturacaoOxigenio,
                             Double frequenciaRespiratoria,
                             Double temperatura,
                             String gravidadePercebida,
                             String sintomasJson,
                             Integer scoreValor,
                             String scoreAlgoritmoVersao,
                             String scoreFatoresJson,
                             Instant criadoEm) {
        this.pacienteId = pacienteId;
        this.frequenciaCardiaca = frequenciaCardiaca;
        this.pressaoArterialSistolica = pressaoArterialSistolica;
        this.pressaoArterialDiastolica = pressaoArterialDiastolica;
        this.saturacaoOxigenio = saturacaoOxigenio;
        this.frequenciaRespiratoria = frequenciaRespiratoria;
        this.temperatura = temperatura;
        this.gravidadePercebida = gravidadePercebida;
        this.sintomas = sintomasJson;
        this.scoreValor = scoreValor;
        this.scoreAlgoritmoVersao = scoreAlgoritmoVersao;
        this.scoreFatores = scoreFatoresJson;
        this.criadoEm = criadoEm;
    }

    public Long getId() {
        return id;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public Double getFrequenciaCardiaca() {
        return frequenciaCardiaca;
    }

    public Double getPressaoArterialSistolica() {
        return pressaoArterialSistolica;
    }

    public Double getPressaoArterialDiastolica() {
        return pressaoArterialDiastolica;
    }

    public Double getSaturacaoOxigenio() {
        return saturacaoOxigenio;
    }

    public Double getFrequenciaRespiratoria() {
        return frequenciaRespiratoria;
    }

    public Double getTemperatura() {
        return temperatura;
    }

    public String getGravidadePercebida() {
        return gravidadePercebida;
    }

    public String getSintomas() {
        return sintomas;
    }

    public Integer getScoreValor() {
        return scoreValor;
    }

    public String getScoreAlgoritmoVersao() {
        return scoreAlgoritmoVersao;
    }

    public String getScoreFatores() {
        return scoreFatores;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
