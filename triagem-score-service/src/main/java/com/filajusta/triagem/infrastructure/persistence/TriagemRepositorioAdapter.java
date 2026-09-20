package com.filajusta.triagem.infrastructure.persistence;

import com.filajusta.triagem.application.port.TriagemRepositorio;
import com.filajusta.triagem.domain.FatorContribuinte;
import com.filajusta.triagem.domain.GravidadePercebida;
import com.filajusta.triagem.domain.Score;
import com.filajusta.triagem.domain.SinaisVitais;
import com.filajusta.triagem.domain.Triagem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter que implementa a porta TriagemRepositorio usando Spring Data JPA.
 */
@Component
public class TriagemRepositorioAdapter implements TriagemRepositorio {
  private final TriagemJpaRepository jpaRepository;
  private final ObjectMapper objectMapper;

  public TriagemRepositorioAdapter(TriagemJpaRepository jpaRepository, ObjectMapper objectMapper) {
    this.jpaRepository = jpaRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<Triagem> buscarPorId(UUID id) {
    return jpaRepository.findById(id)
      .map(this::toDomainEntity);
  }

  @Override
  public void salvar(Triagem triagem) {
    var entity = toJpaEntity(triagem);
    jpaRepository.save(entity);
  }

  private TriagemJpaEntity toJpaEntity(Triagem triagem) {
    try {
      String sinaisVitaisJson = objectMapper.writeValueAsString(triagem.sinaisVitais());
      String sintomasJson = objectMapper.writeValueAsString(triagem.sintomas());
      String factoresJson = objectMapper.writeValueAsString(triagem.score().fatores());

      return new TriagemJpaEntity(
        triagem.id(),
        triagem.pacienteId(),
        triagem.gravidade().toString(),
        sinaisVitaisJson,
        sintomasJson,
        triagem.score().versao(),
        triagem.score().valor(),
        factoresJson,
        triagem.registradoEm()
      );
    } catch (Exception e) {
      throw new RuntimeException("Falha ao serializar Triagem para JPA", e);
    }
  }

  private Triagem toDomainEntity(TriagemJpaEntity entity) {
    try {
      // Desserializa sinais vitais
      Map<String, Object> sinaisVitaisMap = objectMapper.readValue(
        entity.getSinaisVitais(),
        Map.class
      );
      SinaisVitais sinaisVitais = new SinaisVitais(
        ((Number) sinaisVitaisMap.get("frequenciaCardiaca")).doubleValue(),
        ((Number) sinaisVitaisMap.get("pressaoArterialSistolica")).doubleValue(),
        ((Number) sinaisVitaisMap.get("pressaoArterialDiastolica")).doubleValue(),
        ((Number) sinaisVitaisMap.get("saturacaoOxigenio")).doubleValue(),
        ((Number) sinaisVitaisMap.get("frequenciaRespiratoria")).doubleValue(),
        ((Number) sinaisVitaisMap.get("temperatura")).doubleValue()
      );

      // Desserializa sintomas
      @SuppressWarnings("unchecked")
      List<String> sintomas = objectMapper.readValue(entity.getSintomas(), List.class);

      // Desserializa fatores
      List<Map<String, Object>> factoresMap = objectMapper.readValue(
        entity.getScoreFactores(),
        objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
      );
      List<FatorContribuinte> fatores = new ArrayList<>();
      for (Map<String, Object> fatorMap : factoresMap) {
        String fator = (String) fatorMap.get("fator");
        Double contribuicao = ((Number) fatorMap.get("contribuicao")).doubleValue();
        fatores.add(new FatorContribuinte(fator, contribuicao));
      }

      Score score = new Score(entity.getScoreVersao(), entity.getScoreValor(), fatores);
      GravidadePercebida gravidade = GravidadePercebida.valueOf(entity.getGravidade());

      return new Triagem(
        entity.getId(),
        entity.getPacienteId(),
        sinaisVitais,
        gravidade,
        sintomas,
        score,
        entity.getRegistradoEm()
      );
    } catch (Exception e) {
      throw new RuntimeException("Falha ao desserializar Triagem do JPA", e);
    }
  }
}
