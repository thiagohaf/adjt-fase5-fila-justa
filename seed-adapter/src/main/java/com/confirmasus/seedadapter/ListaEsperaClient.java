package com.confirmasus.seedadapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Cliente HTTP para criar entrada de Lista de Espera via gateway-service.
 *
 * Story 5.3: Chama {@code POST /v1/lista-espera} do gateway para criar entrada com CPF,
 * recursoId, dataSolicitacao. Gateway resolve CPF internamente via gRPC (Story 1.1 dependency).
 *
 * <p>Idempotência: gateway deduplica por pacienteId+recursoId naturalmente.
 * Se reexecução encontrar duplicata, tratado como 409 e prossegue.
 *
 * <p>Padrão idêntico a {@code RecursoClient} e {@code AgendamentoClient}.
 * Falha com erro claro se gateway indisponível.
 */
public class ListaEsperaClient {
    private static final Logger logger = LoggerFactory.getLogger(ListaEsperaClient.class);

    private final String gatewayUrl;
    private final AuthClient authClient;
    private final ObjectMapper mapper;

    public ListaEsperaClient(String gatewayUrl, AuthClient authClient) {
        this.gatewayUrl = gatewayUrl;
        this.authClient = authClient;
        this.mapper = new ObjectMapper();
    }

    /**
     * Criar ou obter idempotente de entrada de Lista de Espera via POST /v1/lista-espera.
     * Retorna sucesso sincrono após criação.
     *
     * @param cpf CPF do paciente (em texto claro; gateway resolve internamente)
     * @param recursoId UUID do recurso (já carregado em Story 5.1)
     * @param dataSolicitacao ISO 8601 datetime (ex: "2026-09-10T10:00:00Z")
     * @throws IllegalStateException se CPF/recursoId/dataSolicitacao inválidos (422),
     *         recurso não existe (404), ou gateway indisponível (5xx)
     */
    public void criarOuObter(String cpf, String recursoId, String dataSolicitacao) {
        logger.info("Criando entrada de Lista de Espera: cpf={}, recursoId={}, dataSolicitacao={}",
                maskCpf(cpf), recursoId, dataSolicitacao);

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(gatewayUrl + "/v1/lista-espera");

            // Autentica com JWT
            String token = authClient.obtenerToken();
            post.setHeader("Authorization", "Bearer " + token);

            // Monta payload
            Map<String, Object> body = new HashMap<>();
            body.put("cpf", cpf);
            body.put("recursoId", recursoId);
            body.put("dataSolicitacao", dataSolicitacao);

            String jsonBody = mapper.writeValueAsString(body);
            post.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));

            client.execute(post, response -> {
                int statusCode = response.getCode();
                String responseBody = "";
                try {
                    if (response.getEntity() != null && response.getEntity().getContent() != null) {
                        responseBody = new String(response.getEntity().getContent().readAllBytes());
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("Erro ao ler corpo da resposta: " + e.getMessage(), e);
                }

                if (statusCode == 201) {
                    logger.info("Entrada de Lista de Espera criada com sucesso: cpf={}, recursoId={}, dataSolicitacao={}",
                            maskCpf(cpf), recursoId, dataSolicitacao);
                    return null;
                } else if (statusCode == 409) {
                    // Duplicata: entrada já existe (idempotência)
                    logger.info("Entrada de Lista de Espera já existe (409): cpf={}, recursoId={} — prosseguindo",
                            maskCpf(cpf), recursoId);
                    return null;
                } else if (statusCode == 422) {
                    logger.warn("Validação falhou (422) para entrada cpf={}, recursoId={}",
                            maskCpf(cpf), recursoId);
                    throw new IllegalStateException(
                            "Validação falhou para Lista de Espera cpf=" + maskCpf(cpf) + ", recursoId=" + recursoId);
                } else if (statusCode == 404) {
                    logger.warn("Recurso não encontrado (404) para entrada cpf={}, recursoId={}",
                            maskCpf(cpf), recursoId);
                    throw new IllegalStateException(
                            "Recurso não encontrado para Lista de Espera: recursoId=" + recursoId);
                } else if (statusCode >= 500) {
                    throw new IllegalStateException(
                            "Gateway indisponível (status " + statusCode + ")");
                } else {
                    throw new IllegalStateException(
                            "Falha ao criar entrada de Lista de Espera (status " + statusCode + ")");
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Erro ao chamar gateway: " + e.getMessage(), e);
        }
    }

    /**
     * Mascara CPF para logging (segurança).
     * Exibe apenas os últimos 2 dígitos: 12345678901 -> ****8901
     */
    private String maskCpf(String cpf) {
        if (cpf == null || cpf.length() < 2) {
            return "***";
        }
        return "****" + cpf.substring(cpf.length() - 2);
    }
}
