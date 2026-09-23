package com.confirmasus.seedadapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orquestração de carga de dados sintéticos (Story 5.1).
 *
 * Carrega {@code seed-data.json} e upsertar cada Recurso via RecursoClient.
 * Falha explicitamente se:
 * - Arquivo JSON não encontrado
 * - JSON malformado
 * - Auth-service indisponível (aborta sem prosseguir)
 * - Gateway indisponível (aborta sem prosseguir)
 *
 * <p>Idempotente: reexecução não duplica Recursos (upsert por codigoRecurso).
 */
public class SeedDataLoader {
    private static final Logger logger = LoggerFactory.getLogger(SeedDataLoader.class);

    private final RecursoClient recursoClient;
    private final ObjectMapper mapper;

    public SeedDataLoader(RecursoClient recursoClient) {
        this.recursoClient = recursoClient;
        this.mapper = new ObjectMapper();
    }

    /**
     * Carrega seed-data.json e upsertar todos os Recursos.
     *
     * @throws IllegalStateException se falhar em qualquer etapa (falha explícita)
     */
    public void carregar() {
        logger.info("Iniciando carga de seed-data.json");

        List<RecursoSeed> recursos = carregarJsonSeedData();
        logger.info("Seed-data carregada com sucesso: {} recursos", recursos.size());

        upsertar_recursos(recursos);
        logger.info("Carga de seed-data concluída com sucesso");
    }

    private List<RecursoSeed> carregarJsonSeedData() {
        logger.debug("Carregando seed-data.json do classpath");

        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("seed-data.json")) {
            if (inputStream == null) {
                throw new IllegalStateException(
                        "Arquivo seed-data.json não encontrado no classpath");
            }

            SeedDataContainer container = mapper.readValue(inputStream, SeedDataContainer.class);

            if (container.getRecursos() == null || container.getRecursos().isEmpty()) {
                throw new IllegalStateException("seed-data.json não contém recursos ou está vazio");
            }

            return container.getRecursos();
        } catch (IOException e) {
            throw new IllegalStateException("Erro ao parsear seed-data.json: " + e.getMessage(), e);
        }
    }

    private void upsertar_recursos(List<RecursoSeed> recursos) {
        logger.info("Upsertando {} recursos", recursos.size());

        for (RecursoSeed recurso : recursos) {
            try {
                UUID recursoId = recursoClient.upsertar(
                        recurso.getCodigoRecurso(),
                        recurso.getEspecialidade(),
                        recurso.getUnidade(),
                        recurso.getEspecificidadeRank(),
                        recurso.isDisponivel());

                logger.info("Recurso upsertado com sucesso: codigo={}, recursoId={}",
                        recurso.getCodigoRecurso(), recursoId);
            } catch (IllegalStateException e) {
                logger.error("Falha ao upsertar recurso {}: {}",
                        recurso.getCodigoRecurso(), e.getMessage());
                throw e; // Aborta na primeira falha (falha explícita)
            }
        }
    }

    /**
     * Container para desserialização de {@code seed-data.json}.
     * Estrutura: { "recursos": [ {...}, {...}, ... ] }
     */
    public static class SeedDataContainer {
        private List<RecursoSeed> recursos;

        public List<RecursoSeed> getRecursos() {
            return recursos;
        }

        public void setRecursos(List<RecursoSeed> recursos) {
            this.recursos = recursos;
        }
    }
}
