package com.filajusta.matching.infrastructure.web;

import com.filajusta.matching.application.query.ConsultarFilaPriorizada;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint público do matching-alocacao-service (Story 3.1c): {@code GET
 * /v1/fila} consulta a réplica local de Score priorizada por Prioridade
 * Efetiva (Aging com teto), disparando o bootstrap síncrono a frio quando a
 * réplica está vazia. Sem paginação (Boundaries da spec 3.1c, "Never").
 * Falha do bootstrap vira {@code 503} via {@link FilaExceptionHandler}.
 *
 * <p>Sem autenticação JWT/rota no gateway nesta fase -- mesmo chore de
 * deploy adiado de 2.1/3.0/3.1a/3.1b (deferred-work.md).
 */
@RestController
public class FilaController {

    private final ConsultarFilaPriorizada consultarFilaPriorizada;

    public FilaController(ConsultarFilaPriorizada consultarFilaPriorizada) {
        this.consultarFilaPriorizada = consultarFilaPriorizada;
    }

    @GetMapping("/v1/fila")
    public List<FilaItemResponse> consultar() {
        return consultarFilaPriorizada.consultar().stream()
                .map(FilaItemResponse::de)
                .toList();
    }
}
