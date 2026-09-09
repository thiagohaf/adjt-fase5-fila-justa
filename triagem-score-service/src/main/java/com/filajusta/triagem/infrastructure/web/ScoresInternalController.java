package com.filajusta.triagem.infrastructure.web;

import com.filajusta.triagem.application.query.ListarScoresAtuais;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint interno de bootstrap (Story 3.1a): {@code GET /internal/scores}
 * lista todos os Scores atuais para o {@code matching-alocacao-service}
 * popular sua replica local em boot a frio (Stories 3.1b/3.1c) -- gap
 * deixado propositalmente em aberto pela Story 2.2 ate o Epic 3 precisar.
 *
 * <p>Chamada servico-a-servico: nao exige JWT nem passa pelo {@code
 * gateway-service} (mesmo padrao de escopo minimo de {@code
 * ConsultarTriagem}); esta rota NUNCA deve ser exposta via {@code
 * gateway-service}/CDK (Boundaries/Never da spec 3.1a). Sem paginacao nem
 * filtros -- banco vazio devolve lista vazia com {@code 200}, nunca erro.
 */
@RestController
public class ScoresInternalController {

    private final ListarScoresAtuais listarScoresAtuais;

    public ScoresInternalController(ListarScoresAtuais listarScoresAtuais) {
        this.listarScoresAtuais = listarScoresAtuais;
    }

    @GetMapping("/internal/scores")
    public List<ScoreAtualResponse> listar() {
        return listarScoresAtuais.listar().stream().map(ScoreAtualResponse::de).toList();
    }
}
