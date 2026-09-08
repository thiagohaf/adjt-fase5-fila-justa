package com.filajusta.triagem.infrastructure.web;

import com.filajusta.triagem.application.command.RegistrarTriagem;
import com.filajusta.triagem.domain.Triagem;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unico endpoint publico do triagem-score-service nesta fase (FR-1, FR-3):
 * {@code POST /v1/triagens}. Sempre responde {@code 201} de forma sincrona
 * quando os dados sao validos (Boundaries da spec 2.1) -- erros de
 * validacao viram {@code 400} RFC 7807 via {@link TriagemExceptionHandler},
 * que trata as excecoes unicas lancadas pelo dominio dentro de
 * {@link RegistrarTriagem}.
 */
@RestController
public class TriagemController {

    private final RegistrarTriagem registrarTriagem;

    public TriagemController(RegistrarTriagem registrarTriagem) {
        this.registrarTriagem = registrarTriagem;
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
}
