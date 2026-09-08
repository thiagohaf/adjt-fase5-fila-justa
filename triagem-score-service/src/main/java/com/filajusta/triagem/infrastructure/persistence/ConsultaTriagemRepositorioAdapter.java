package com.filajusta.triagem.infrastructure.persistence;

import com.filajusta.triagem.application.query.ConsultaTriagemRepositorio;
import com.filajusta.triagem.domain.FatorContribuinte;
import com.filajusta.triagem.domain.GravidadePercebida;
import com.filajusta.triagem.domain.LimitesSinaisVitais;
import com.filajusta.triagem.domain.Score;
import com.filajusta.triagem.domain.SinaisVitais;
import com.filajusta.triagem.domain.Triagem;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

/**
 * Adapter que implementa a porta {@link ConsultaTriagemRepositorio}
 * (application/query) usando {@link TriagemJpaRepository} (Spring Data,
 * schema {@code triagem_score}). Desserializa {@code sintomas} e
 * {@code score_fatores} (colunas {@code jsonb}) com o mesmo
 * {@link ObjectMapper} (Jackson 3) usado na escrita por
 * {@link TriagemRepositorioAdapter} -- inverso da mesma serializacao -- e
 * reconstroi {@link SinaisVitais} com o {@link LimitesSinaisVitais} ja
 * existente (mesmos limites usados no registro, AD-11).
 *
 * <p>Os dados persistidos ja passaram pela validacao de
 * {@link SinaisVitais}/{@link GravidadePercebida} no registro (Story 2.1),
 * entao esta reconstrucao nunca deveria lancar as excecoes de validacao do
 * dominio (Design Notes da spec 2.2).
 */
@Component
class ConsultaTriagemRepositorioAdapter implements ConsultaTriagemRepositorio {

    private final TriagemJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;
    private final LimitesSinaisVitais limites;

    ConsultaTriagemRepositorioAdapter(TriagemJpaRepository jpaRepository,
                                       ObjectMapper objectMapper,
                                       LimitesSinaisVitais limites) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
        this.limites = limites;
    }

    @Override
    public Optional<Triagem> buscarPorId(Long id) {
        return jpaRepository.findById(id).map(this::paraDominio);
    }

    private Triagem paraDominio(TriagemJpaEntity entity) {
        SinaisVitais sinaisVitais = new SinaisVitais(
                entity.getFrequenciaCardiaca(),
                entity.getPressaoArterialSistolica(),
                entity.getPressaoArterialDiastolica(),
                entity.getSaturacaoOxigenio(),
                entity.getFrequenciaRespiratoria(),
                entity.getTemperatura(),
                limites);
        GravidadePercebida gravidadePercebida = GravidadePercebida.fromTexto(entity.getGravidadePercebida());
        List<String> sintomas = List.of(ler(entity.getSintomas(), String[].class));
        List<FatorContribuinte> fatores = List.of(ler(entity.getScoreFatores(), FatorContribuinte[].class));
        Score score = new Score(entity.getScoreValor(), entity.getScoreAlgoritmoVersao(), fatores);

        return new Triagem(entity.getId(), entity.getPacienteId(), sinaisVitais, gravidadePercebida,
                sintomas, score, entity.getCriadoEm());
    }

    private <T> T[] ler(String json, Class<T[]> tipoArray) {
        return objectMapper.readValue(json, tipoArray);
    }
}
