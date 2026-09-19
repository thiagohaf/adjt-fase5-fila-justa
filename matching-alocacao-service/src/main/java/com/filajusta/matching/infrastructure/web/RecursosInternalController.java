package com.filajusta.matching.infrastructure.web;

import com.filajusta.matching.application.command.UpsertRecurso;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint interno de upsert de {@code Recurso} (Story 3.2b2): {@code POST
 * /internal/recursos} cria/atualiza idempotentemente um Recurso por {@code
 * codigoRecurso} -- consumido pelo {@code seed-adapter} do Epic 5 e pela
 * Story 3-2b3 (sugestão com tiers) para popular o catálogo de Recursos do
 * matching-alocacao-service.
 *
 * <p>Chamada serviço-a-serviço: sem JWT nem rota no {@code gateway-service}
 * (mesmo padrão de escopo mínimo de {@code GET /internal/scores}/{@code GET
 * /v1/fila} -- nenhum serviço tem Spring Security ainda, Boundaries da spec
 * 3.2b2); esta rota NUNCA deve ser exposta via {@code
 * gateway-service}/CDK.
 *
 * <p>{@code 400} de {@code UpsertRecursoRequest} inválido é traduzido para
 * RFC 7807 por {@link RecursosExceptionHandler}.
 */
@RestController
public class RecursosInternalController {

    private final UpsertRecurso upsertRecurso;

    public RecursosInternalController(UpsertRecurso upsertRecurso) {
        this.upsertRecurso = upsertRecurso;
    }

    @PostMapping("/internal/recursos")
    public ResponseEntity<RecursoResponse> upsert(@Valid @RequestBody UpsertRecursoRequest request) {
        UpsertRecurso.Resultado resultado = upsertRecurso.upsertar(
                request.codigoRecurso(), request.especificidadeRank(), request.disponivel());

        HttpStatus status = resultado.criado() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(RecursoResponse.de(resultado.recurso()));
    }
}
