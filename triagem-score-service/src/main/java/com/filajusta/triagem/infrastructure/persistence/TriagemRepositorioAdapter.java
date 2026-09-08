package com.filajusta.triagem.infrastructure.persistence;

import com.filajusta.triagem.application.command.TriagemRepositorio;
import com.filajusta.triagem.domain.SinaisVitais;
import com.filajusta.triagem.domain.Score;
import com.filajusta.triagem.domain.Triagem;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Adapter que implementa a porta {@link TriagemRepositorio}
 * (application/command) usando {@link TriagemJpaRepository} (Spring Data,
 * schema {@code triagem_score}). Serializa {@code sintomas} e os fatores do
 * {@link Score} para JSON (colunas {@code jsonb}) usando o
 * {@link ObjectMapper} (Jackson 3) compartilhado do Spring Boot 4.1.
 */
@Component
class TriagemRepositorioAdapter implements TriagemRepositorio {

    private final TriagemJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;

    TriagemRepositorioAdapter(TriagemJpaRepository jpaRepository, ObjectMapper objectMapper) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Triagem salvar(Triagem triagem) {
        SinaisVitais sv = triagem.getSinaisVitais();
        Score score = triagem.getScore();

        TriagemJpaEntity entity = new TriagemJpaEntity(
                triagem.getPacienteId(),
                sv.getFrequenciaCardiaca(),
                sv.getPressaoArterialSistolica(),
                sv.getPressaoArterialDiastolica(),
                sv.getSaturacaoOxigenio(),
                sv.getFrequenciaRespiratoria(),
                sv.getTemperatura(),
                triagem.getGravidadePercebida().name(),
                escrever(triagem.getSintomas()),
                score.getValor(),
                score.getAlgoritmoVersao(),
                escrever(score.getFatores()),
                triagem.getCriadoEm());

        TriagemJpaEntity salvo = jpaRepository.save(entity);
        return triagem.comId(salvo.getId());
    }

    private String escrever(Object valor) {
        return objectMapper.writeValueAsString(valor);
    }
}
