package com.filajusta.matching.infrastructure.web;

import com.filajusta.matching.application.command.ConfirmarAlocacao;
import com.filajusta.matching.domain.Alocacao;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoint público do matching-alocacao-service (Story 3-3b1): {@code POST
 * /v1/recursos/{id}/alocacoes} confirma a Sugestão de Matching, criando uma
 * {@link Alocacao}. Lê o header {@code X-Correlation-Id} (mesmo padrão de
 * {@code TriagemController}, triagem-score-service) e repassa a
 * {@link ConfirmarAlocacao} -- a geração de um UUID quando o header está
 * ausente é responsabilidade do caso de uso, não deste controller.
 *
 * <p>{@code id} não-UUID no path vira {@code 400} (tradução automática do
 * Spring, mesmo padrão de {@code RecursoSugestaoController}); {@code 404}
 * (Recurso inexistente) e os 2 cenários de {@code 409} são traduzidos para
 * RFC 7807 por {@link RecursosExceptionHandler}.
 *
 * <p>Sem autenticação JWT/rota no gateway nesta fase -- mesmo padrão de
 * {@link RecursoSugestaoController} (roteamento no gateway ainda pendente,
 * gap pré-existente, não desta story).
 */
@RestController
public class AlocacaoController {

    private final ConfirmarAlocacao confirmarAlocacao;

    public AlocacaoController(ConfirmarAlocacao confirmarAlocacao) {
        this.confirmarAlocacao = confirmarAlocacao;
    }

    @PostMapping("/v1/recursos/{id}/alocacoes")
    @ResponseStatus(HttpStatus.CREATED)
    public AlocacaoResponse confirmar(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ConfirmarAlocacaoRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        Alocacao alocacao = confirmarAlocacao.confirmar(id, request.pacienteId(), correlationId);
        return AlocacaoResponse.de(alocacao);
    }
}
