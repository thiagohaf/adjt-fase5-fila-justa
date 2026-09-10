package com.filajusta.triagem.application.query;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Caso de uso de {@code GET /internal/scores} (Story 3.1a): lista todos os
 * Scores atuais, sem paginacao, para o {@code matching-alocacao-service}
 * popular sua replica local em boot a frio -- gap deixado propositalmente em
 * aberto pela Story 2.2 (ver {@code spec-2-2-consulta-triagem-score-fatores-contribuintes.md},
 * Boundaries/Never) ate o Epic 3 precisar. Endpoint interno, servico-a-servico
 * -- sem JWT, sem passar pelo {@code gateway-service} (mesmo padrao de
 * escopo minimo de {@link ConsultarTriagem}).
 *
 * <p>Nao muta estado, por isso vive em {@code application/query} (CQRS
 * logico da arquitetura), com {@code @Transactional(readOnly = true)} igual
 * a {@link ConsultarTriagem}. Banco vazio devolve lista vazia, nunca erro
 * (Boundaries da spec 3.1a).
 */
public class ListarScoresAtuais {

    private final ScoresAtuaisRepositorio scoresAtuaisRepositorio;

    public ListarScoresAtuais(ScoresAtuaisRepositorio scoresAtuaisRepositorio) {
        this.scoresAtuaisRepositorio = scoresAtuaisRepositorio;
    }

    @Transactional(readOnly = true)
    public List<ScoreAtual> listar() {
        return scoresAtuaisRepositorio.listarTodos();
    }
}
