package com.confirmasus.matching.infrastructure.web;

import com.confirmasus.matching.application.command.UpsertRecurso;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint público de upsert de {@code Recurso} (Story 5.1): {@code POST
 * /v1/recursos} cria/atualiza idempotentemente um Recurso por {@code
 * codigoRecurso} -- consumido pelo {@code seed-adapter} do Epic 5 via
 * gateway com autenticação JWT.
 *
 * <p>Rota pública (exposta via gateway-service): seed-adapter autentica
 * com JWT de usuário técnico, passado no header Authorization (validado
 * pelo gateway). Este controller recebe o request já autenticado.
 *
 * <p>{@code 400} de {@code UpsertRecursoRequest} inválido é traduzido para
 * RFC 7807 por {@link RecursosExceptionHandler}.
 */
@RestController
public class RecursoPublicController {

    private final UpsertRecurso upsertRecurso;

    public RecursoPublicController(UpsertRecurso upsertRecurso) {
        this.upsertRecurso = upsertRecurso;
    }

    @PostMapping("/v1/recursos")
    public ResponseEntity<RecursoResponse> upsert(@Valid @RequestBody UpsertRecursoRequest request) {
        UpsertRecurso.Resultado resultado = upsertRecurso.upsertar(
                request.codigoRecurso(), request.especificidadeRank(), request.disponivel(),
                request.especialidade(), request.unidade());

        HttpStatus status = resultado.criado() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(RecursoResponse.de(resultado.recurso()));
    }
}
