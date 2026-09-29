package com.confirmasus.matching.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface RecursoJpaRepository extends JpaRepository<RecursoJpaEntity, UUID> {

    // Upsert direto e idempotente por codigo_recurso -- sem WHERE temporal:
    // o DO UPDATE sempre aplica num conflito, last-write-wins simples, sem
    // comparacao de occurred_at/event_id (Recurso nao tem esses campos). O
    // DO UPDATE deliberadamente NAO inclui recurso_id no SET -- um upsert
    // repetido para o mesmo codigo_recurso sempre preserva o recurso_id ja
    // persistido na primeira insercao, mesmo que o candidato desta chamada
    // traga um recurso_id novo gerado por UpsertRecurso (ver seu javadoc).
    @Modifying
    @Query(value = "INSERT INTO matching_alocacao.recurso "
            + "(recurso_id, codigo_recurso, especificidade_rank, disponivel, especialidade, unidade) "
            + "VALUES (:recursoId, :codigoRecurso, :especificidadeRank, :disponivel, :especialidade, :unidade) "
            + "ON CONFLICT (codigo_recurso) DO UPDATE SET "
            + "especificidade_rank = excluded.especificidade_rank, "
            + "disponivel = excluded.disponivel, "
            + "especialidade = excluded.especialidade, "
            + "unidade = excluded.unidade",
            nativeQuery = true)
    void upsert(@Param("recursoId") UUID recursoId,
                @Param("codigoRecurso") String codigoRecurso,
                @Param("especificidadeRank") int especificidadeRank,
                @Param("disponivel") boolean disponivel,
                @Param("especialidade") String especialidade,
                @Param("unidade") String unidade);

    // Le de volta o estado efetivamente persistido logo apos o upsert nativo
    // acima -- e assim que RecursoRepositorioAdapter descobre o recurso_id
    // real (preservado ou recem-gerado) para devolver a UpsertRecurso, que
    // por sua vez decide criado (201) vs atualizado (200) comparando esse
    // valor com o candidato que enviou (ver javadoc de UpsertRecurso).
    Optional<RecursoJpaEntity> findByCodigoRecurso(String codigoRecurso);

    // ConfirmarAlocacao: marca o Recurso indisponivel apos uma confirmacao
    // aceita. JPQL simples (nao native, ao contrario do upsert acima) -- e
    // so um UPDATE de 1 coluna por PK, sem necessidade de SQL nativo. Sem
    // WHERE disponivel = true: idempotente por natureza (uma segunda
    // chamada para o mesmo recursoId ja indisponivel nao falha, so nao
    // muda nada).
    @Modifying
    @Query("UPDATE RecursoJpaEntity r SET r.disponivel = false WHERE r.recursoId = :recursoId")
    void marcarIndisponivel(@Param("recursoId") UUID recursoId);
}
