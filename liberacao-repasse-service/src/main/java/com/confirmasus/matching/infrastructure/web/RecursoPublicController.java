package com.confirmasus.matching.infrastructure.web;

import com.confirmasus.matching.application.command.UpsertRecurso;
import com.confirmasus.matching.application.query.RecursoConsultaRepositorio;
import com.confirmasus.matching.application.query.RecursoNaoEncontradoException;
import com.confirmasus.matching.domain.Recurso;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

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
    private final RecursoConsultaRepositorio recursoConsultaRepositorio;

    public RecursoPublicController(UpsertRecurso upsertRecurso,
                                    RecursoConsultaRepositorio recursoConsultaRepositorio) {
        this.upsertRecurso = upsertRecurso;
        this.recursoConsultaRepositorio = recursoConsultaRepositorio;
    }

    @PostMapping("/v1/recursos")
    public ResponseEntity<RecursoResponse> upsert(@Valid @RequestBody UpsertRecursoRequest request) {
        UpsertRecurso.Resultado resultado = upsertRecurso.upsertar(
                request.codigoRecurso(), request.especificidadeRank(), request.disponivel(),
                request.especialidade(), request.unidade());

        HttpStatus status = resultado.criado() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(RecursoResponse.de(resultado.recurso()));
    }

    /**
     * Consulta pontual de um Recurso pelo {@code recursoId} -- faltava uma
     * forma pública de resolver {@code codigoRecurso}/{@code especialidade}/
     * {@code unidade} a partir do {@code recursoId} opaco que os outros
     * serviços (agendamento-confirmacao-service) só guardam como UUID; sem
     * isso o frontend não tinha como exibir o nome do recurso, só o UUID
     * bruto. Reaproveita {@link RecursoConsultaRepositorio#buscarPorId} já
     * usado por {@code ConsultarSugestaoRecurso} -- mesmo padrão de 404 via
     * {@link RecursoNaoEncontradoException}.
     */
    @GetMapping("/v1/recursos/{id}")
    public ResponseEntity<RecursoResponse> consultar(@PathVariable("id") UUID id) {
        Recurso recurso = recursoConsultaRepositorio.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(id));
        return ResponseEntity.ok(RecursoResponse.de(recurso));
    }
}
