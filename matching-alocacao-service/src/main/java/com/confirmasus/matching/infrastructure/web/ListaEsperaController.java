package com.confirmasus.matching.infrastructure.web;

import com.confirmasus.matching.application.command.CriarEntradaListaEspera;
import com.confirmasus.matching.domain.ListaEsperaEntrada;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Endpoints de Lista de Espera (Story 5.3).
 * POST /v1/lista-espera: criar entrada de lista de espera.
 */
@RestController
public class ListaEsperaController {

    private final CriarEntradaListaEspera criarEntradaListaEspera;

    public ListaEsperaController(CriarEntradaListaEspera criarEntradaListaEspera) {
        this.criarEntradaListaEspera = criarEntradaListaEspera;
    }

    @PostMapping("/v1/lista-espera")
    @ResponseStatus(HttpStatus.CREATED)
    public CriarEntradaListaEsperaResponse criar(@Valid @RequestBody CriarEntradaListaEsperaRequest request) {
        ListaEsperaEntrada entrada = criarEntradaListaEspera.criar(
                request.cpf(),
                request.recursoId(),
                Instant.parse(request.dataSolicitacao())
        );
        return CriarEntradaListaEsperaResponse.de(entrada);
    }
}
