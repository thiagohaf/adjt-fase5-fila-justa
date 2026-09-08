package com.filajusta.triagem.infrastructure.web;

import com.filajusta.triagem.application.command.RegistrarTriagem;
import com.filajusta.triagem.application.query.ConsultarTriagem;
import com.filajusta.triagem.domain.Triagem;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints publicos do triagem-score-service (FR-1, FR-3): {@code POST
 * /v1/triagens} sempre responde {@code 201} de forma sincrona quando os
 * dados sao validos (Boundaries da spec 2.1), e {@code GET
 * /v1/triagens/{id}} (Story 2.2) consulta uma Triagem ja registrada,
 * devolvendo o Score persistido -- nunca recalculado. Erros de validacao e
 * consulta viram RFC 7807 via {@link TriagemExceptionHandler}, que trata as
 * excecoes unicas lancadas pelo dominio dentro de {@link RegistrarTriagem} e
 * {@link ConsultarTriagem}.
 */
@RestController
public class TriagemController {

    private final RegistrarTriagem registrarTriagem;
    private final ConsultarTriagem consultarTriagem;

    public TriagemController(RegistrarTriagem registrarTriagem, ConsultarTriagem consultarTriagem) {
        this.registrarTriagem = registrarTriagem;
        this.consultarTriagem = consultarTriagem;
    }

    @PostMapping("/v1/triagens")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrarTriagemResponse registrar(@RequestBody RegistrarTriagemRequest request) {
        RegistrarTriagemRequest.SinaisVitaisRequest sinaisVitais = request.sinaisVitais();

        Triagem triagem = registrarTriagem.registrar(
                request.cpf(),
                sinaisVitais == null ? null : sinaisVitais.frequenciaCardiaca(),
                sinaisVitais == null ? null : sinaisVitais.pressaoArterialSistolica(),
                sinaisVitais == null ? null : sinaisVitais.pressaoArterialDiastolica(),
                sinaisVitais == null ? null : sinaisVitais.saturacaoOxigenio(),
                sinaisVitais == null ? null : sinaisVitais.frequenciaRespiratoria(),
                sinaisVitais == null ? null : sinaisVitais.temperatura(),
                request.gravidadePercebida(),
                request.sintomas());

        return RegistrarTriagemResponse.de(triagem);
    }

    @GetMapping("/v1/triagens/{id}")
    public ConsultarTriagemResponse consultar(@PathVariable("id") Long id) {
        Triagem triagem = consultarTriagem.consultar(id);
        return ConsultarTriagemResponse.de(triagem);
    }
}
