package com.filajusta.triagem.infrastructure.web;

import com.filajusta.triagem.application.command.RegistrarTriagem;
import com.filajusta.triagem.application.command.TriagemRegistradaResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint REST para registro de Triagem.
 * POST /v1/triagens: registra uma triagem e retorna Score calculado.
 */
@RestController
@RequestMapping("/v1/triagens")
public class TriagemController {
  private final RegistrarTriagem registrarTriagem;

  public TriagemController(RegistrarTriagem registrarTriagem) {
    this.registrarTriagem = registrarTriagem;
  }

  @PostMapping
  public ResponseEntity<TriagemRegistradaResponse> registrarTriagem(
    @Valid @RequestBody RegistrarTriagemRequest request
  ) {
    TriagemRegistradaResponse response = registrarTriagem.executar(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
