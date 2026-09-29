package com.confirmasus.matching.infrastructure.web;

import com.confirmasus.matching.application.command.ConfirmarRepasse;
import com.confirmasus.matching.application.command.RecusarSugestaoRepasse;
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
 * Decisão humana do Gestor sobre a Sugestão de Repasse (Story 6.1, AD-6):
 * {@code POST /v1/sugestoes-repasse/{id}/confirmacao} e {@code .../recusa}.
 * {@code 404} sugestão inexistente, {@code 409} já decidida (perdeu a
 * corrida) -- traduzidos por {@link RecursosExceptionHandler}.
 */
@RestController
public class SugestaoRepasseController {

    private final ConfirmarRepasse confirmarRepasse;
    private final RecusarSugestaoRepasse recusarSugestaoRepasse;

    public SugestaoRepasseController(ConfirmarRepasse confirmarRepasse,
                                     RecusarSugestaoRepasse recusarSugestaoRepasse) {
        this.confirmarRepasse = confirmarRepasse;
        this.recusarSugestaoRepasse = recusarSugestaoRepasse;
    }

    @PostMapping("/v1/sugestoes-repasse/{id}/confirmacao")
    @ResponseStatus(HttpStatus.CREATED)
    public AlocacaoResponse confirmar(
            @PathVariable("id") UUID id,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return AlocacaoResponse.de(confirmarRepasse.confirmar(id, correlationId));
    }

    @PostMapping("/v1/sugestoes-repasse/{id}/recusa")
    public RecusarSugestaoRepasseResponse recusar(
            @PathVariable("id") UUID id,
            @Valid @RequestBody RecusarSugestaoRepasseRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return RecusarSugestaoRepasseResponse.de(
                recusarSugestaoRepasse.recusar(id, request.motivo(), correlationId));
    }
}
