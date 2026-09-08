package com.filajusta.triagem.application.query;

import com.filajusta.triagem.domain.Triagem;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso de {@code GET /v1/triagens/{id}} (FR-3, Story 2.2): busca a
 * Triagem persistida e devolve exatamente o Score (valor, versao, fatores)
 * gravado no registro original -- nunca recalcula (Boundaries da spec 2.2).
 * Nao muta estado, por isso vive em {@code application/query} (CQRS logico
 * da arquitetura), com {@code @Transactional(readOnly = true)} em vez do
 * {@code @Transactional} de escrita de {@link com.filajusta.triagem.application.command.RegistrarTriagem}.
 */
public class ConsultarTriagem {

    private final ConsultaTriagemRepositorio consultaTriagemRepositorio;

    public ConsultarTriagem(ConsultaTriagemRepositorio consultaTriagemRepositorio) {
        this.consultaTriagemRepositorio = consultaTriagemRepositorio;
    }

    @Transactional(readOnly = true)
    public Triagem consultar(Long id) {
        return consultaTriagemRepositorio.buscarPorId(id)
                .orElseThrow(() -> new TriagemNaoEncontradaException(id));
    }
}
