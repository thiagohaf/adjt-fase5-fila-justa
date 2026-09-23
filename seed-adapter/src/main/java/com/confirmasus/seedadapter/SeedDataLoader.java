package com.confirmasus.seedadapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * Orquestração de carga de dados sintéticos (Stories 5.1, 5.2, 5.3+).
 *
 * Carrega {@code seed-data.json} e:
 * 1. Upsertar cada Recurso via RecursoClient (Story 5.1)
 * 2. Carregar cada Agendamento com transições de estado via AgendamentoClient (Story 5.2)
 * 3. (Futura) Carregar Lista de Espera via ListaEsperaClient (Story 5.3)
 *
 * Falha explicitamente se:
 * - Arquivo JSON não encontrado
 * - JSON malformado
 * - Auth-service indisponível (aborta sem prosseguir)
 * - Gateway indisponível (aborta sem prosseguir)
 * - Qualquer etapa falha (não prossegue para etapa seguinte)
 *
 * <p>Ordem de execução rigorosa: Recursos → Agendamentos → Lista de Espera.
 * <p>Idempotente: reexecução não duplica (upsert por codigoRecurso para Recurso,
 * CPF+recursoId+dataHora para Agendamento).
 */
public class SeedDataLoader {
    private static final Logger logger = LoggerFactory.getLogger(SeedDataLoader.class);

    private final RecursoClient recursoClient;
    private final AgendamentoClient agendamentoClient;
    private final ObjectMapper mapper;

    public SeedDataLoader(RecursoClient recursoClient, AgendamentoClient agendamentoClient) {
        this.recursoClient = recursoClient;
        this.agendamentoClient = agendamentoClient;
        this.mapper = new ObjectMapper();
    }

    /**
     * Construtor para compatibilidade com Story 5.1 (apenas Recursos, sem Agendamentos).
     */
    public SeedDataLoader(RecursoClient recursoClient) {
        this(recursoClient, null);
    }

    /**
     * Carrega seed-data.json e executa pipeline de ingestão:
     * 1. Upsertar Recursos
     * 2. Carregar Agendamentos (se disponível e AgendamentoClient configurado)
     *
     * Execução rigorosa: falha em qualquer etapa aborta pipeline.
     *
     * @throws IllegalStateException se falhar em qualquer etapa (falha explícita)
     */
    public void carregar() {
        logger.info("Iniciando carga de seed-data.json");

        SeedDataContainer container = carregarJsonSeedData();
        logger.info("Seed-data carregada: {} recursos, {} agendamentos",
                container.getRecursos() != null ? container.getRecursos().size() : 0,
                container.getAgendamentos() != null ? container.getAgendamentos().size() : 0);

        // Etapa 1: Upsertar Recursos (Story 5.1)
        if (container.getRecursos() != null && !container.getRecursos().isEmpty()) {
            upsertar_recursos(container.getRecursos());
        }

        // Etapa 2: Carregar Agendamentos (Story 5.2)
        if (container.getAgendamentos() != null && !container.getAgendamentos().isEmpty()) {
            if (agendamentoClient == null) {
                throw new IllegalStateException(
                        "AgendamentoClient não configurado, mas seed-data contém agendamentos. " +
                        "Forneça AgendamentoClient no construtor de SeedDataLoader.");
            }
            carregarAgendamentos(container.getAgendamentos());
        }

        logger.info("Carga de seed-data concluída com sucesso");
    }

    private SeedDataContainer carregarJsonSeedData() {
        logger.debug("Carregando seed-data.json do classpath");

        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("seed-data.json")) {
            if (inputStream == null) {
                throw new IllegalStateException(
                        "Arquivo seed-data.json não encontrado no classpath");
            }

            SeedDataContainer container = mapper.readValue(inputStream, SeedDataContainer.class);

            if (container.getRecursos() == null || container.getRecursos().isEmpty()) {
                logger.warn("seed-data.json não contém recursos");
            }

            return container;
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
     * Carrega Agendamentos via AgendamentoClient com orquestração de transições de estado.
     *
     * Para cada Agendamento:
     * 1. Cria via POST /v1/agendamentos (retorna agendamentoId, estado AGUARDANDO_JANELA)
     * 2. Orquestra transições de estado até atingir estado desejado (confirma/recusa)
     *
     * Falha na primeira entrada inválida (422) pula, mas falha explícita se gateway indisponível (5xx).
     *
     * @param agendamentos lista de AgendamentoSeed com cpf, recursoId, dataHora, estado
     * @throws IllegalStateException se gateway indisponível (aborta pipeline)
     */
    private void carregarAgendamentos(List<AgendamentoSeed> agendamentos) {
        logger.info("Carregando {} agendamentos", agendamentos.size());

        for (AgendamentoSeed agendamento : agendamentos) {
            try {
                // Etapa 1: Criar Agendamento (estado inicial AGUARDANDO_JANELA)
                UUID agendamentoId = agendamentoClient.criarOuObter(
                        agendamento.getCpf(),
                        agendamento.getRecursoId(),
                        agendamento.getDataHoraAgendamento());

                logger.info("Agendamento criado: agendamentoId={}, recursoId={}, dataHora={}",
                        agendamentoId, agendamento.getRecursoId(), agendamento.getDataHoraAgendamento());

                // Etapa 2: Transicionar para estado desejado (se não é AGUARDANDO_JANELA)
                String estadoDesejado = agendamento.getEstado();
                if (estadoDesejado != null && !estadoDesejado.isEmpty()
                        && !estadoDesejado.equals("AGUARDANDO_JANELA")) {
                    agendamentoClient.transicionarParaEstado(agendamentoId, estadoDesejado);
                    logger.info("Agendamento transicionado para estado {}: agendamentoId={}",
                            estadoDesejado, agendamentoId);
                } else {
                    logger.info("Agendamento mantém estado inicial AGUARDANDO_JANELA: agendamentoId={}",
                            agendamentoId);
                }

            } catch (IllegalStateException e) {
                // Distinguir entre erro de validação (422 — pula entrada) e erro de gateway (5xx — aborta)
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.contains("Validação falhou")) {
                    logger.warn("Validação falhou para agendamento — entrada pulada: {}",
                            errorMsg);
                    // Continua com próxima entrada
                } else if (errorMsg != null && errorMsg.contains("indisponível")) {
                    logger.error("Gateway indisponível — aborta pipeline de agendamentos: {}",
                            errorMsg);
                    throw e; // Aborta pipeline (falha explícita)
                } else {
                    logger.error("Falha ao carregar agendamento: {}", errorMsg);
                    throw e; // Aborta em erro inesperado
                }
            }
        }

        logger.info("Carregamento de agendamentos concluído");
    }

    /**
     * Container para desserialização de {@code seed-data.json}.
     * Estrutura:
     * {
     *   "recursos": [ {...}, {...}, ... ],
     *   "agendamentos": [ {...}, {...}, ... ]
     * }
     */
    public static class SeedDataContainer {
        private List<RecursoSeed> recursos;
        private List<AgendamentoSeed> agendamentos;

        public List<RecursoSeed> getRecursos() {
            return recursos;
        }

        public void setRecursos(List<RecursoSeed> recursos) {
            this.recursos = recursos;
        }

        public List<AgendamentoSeed> getAgendamentos() {
            return agendamentos;
        }

        public void setAgendamentos(List<AgendamentoSeed> agendamentos) {
            this.agendamentos = agendamentos;
        }
    }
}
