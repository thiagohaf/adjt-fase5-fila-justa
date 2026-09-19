package com.filajusta.matching.application.command;

import com.filajusta.matching.domain.Recurso;

import java.util.UUID;

/**
 * Caso de uso de upsert de {@link Recurso} (Story 3.2b2, Code Map):
 * consumido por {@code POST /internal/recursos} ({@code
 * infrastructure.web.RecursosInternalController}). Upsert direto e
 * idempotente por {@code codigoRecurso} -- sem comparação temporal, ao
 * contrário de {@link AtualizarScoreReplica} (Boundaries da spec 3.2b2):
 * cria um {@code Recurso} novo (com um {@code recursoId} UUID v4 gerado
 * aqui) quando {@code codigoRecurso} é inédito, ou atualiza {@code
 * especificidadeRank}/{@code disponivel} preservando o {@code recursoId} já
 * existente quando já cadastrado.
 *
 * <p>O {@code recursoId} gerado aqui é só um <em>candidato</em> para o caso
 * de inserção -- o upsert nativo ({@code RecursoJpaRepository#upsert},
 * acionado via {@link RecursoRepositorio#upsert(Recurso)}) nunca sobrescreve
 * {@code recurso_id} num conflito por {@code codigoRecurso}, então uma
 * atualização sempre preserva o {@code recursoId} já persistido
 * independente do candidato enviado. {@link RecursoRepositorio#upsert}
 * devolve o {@code Recurso} efetivamente persistido (não o candidato) -- é
 * assim que {@link #upsertar} decide {@code criado} (recursoId persistido
 * == candidato, {@code 201}) vs atualizado (recursoId persistido != o
 * candidato descartado, {@code 200}), delegado ao controller.
 */
public class UpsertRecurso {

    private final RecursoRepositorio repositorio;

    public UpsertRecurso(RecursoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    public Resultado upsertar(String codigoRecurso, int especificidadeRank, boolean disponivel) {
        UUID recursoIdCandidato = UUID.randomUUID();
        Recurso candidato = new Recurso(recursoIdCandidato, codigoRecurso, especificidadeRank, disponivel);

        Recurso persistido = repositorio.upsert(candidato);

        boolean criado = persistido.getRecursoId().equals(recursoIdCandidato);
        return new Resultado(persistido, criado);
    }

    /**
     * {@code criado}: {@code true} quando o upsert efetivamente inseriu uma
     * linha nova ({@code codigoRecurso} inédito); {@code false} quando
     * atualizou uma linha já existente (mesmo {@code recursoId} preservado).
     * Consumido pelo controller para decidir {@code 201} vs {@code 200}
     * (I/O &amp; Edge-Case Matrix da spec 3.2b2).
     */
    public record Resultado(Recurso recurso, boolean criado) {
    }
}
