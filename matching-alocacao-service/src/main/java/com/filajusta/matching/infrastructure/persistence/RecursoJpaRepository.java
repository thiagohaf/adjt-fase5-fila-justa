package com.filajusta.matching.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface RecursoJpaRepository extends JpaRepository<RecursoJpaEntity, UUID> {

    // Upsert direto e idempotente por codigo_recurso (Boundaries da spec
    // 3.2b2) -- sem WHERE temporal (difere de
    // ScoreReplicaJpaRepository#upsertSeMaisRecente): o DO UPDATE sempre
    // aplica num conflito, last-write-wins simples, sem comparacao de
    // occurred_at/event_id (Recurso nao tem esses campos). O DO UPDATE
    // deliberadamente NAO inclui recurso_id no SET -- um upsert repetido
    // para o mesmo codigo_recurso sempre preserva o recurso_id ja
    // persistido na primeira insercao, mesmo que o candidato desta chamada
    // traga um recurso_id novo gerado por UpsertRecurso (ver seu javadoc).
    @Modifying
    @Query(value = "INSERT INTO matching_alocacao.recurso "
            + "(recurso_id, codigo_recurso, especificidade_rank, disponivel) "
            + "VALUES (:recursoId, :codigoRecurso, :especificidadeRank, :disponivel) "
            + "ON CONFLICT (codigo_recurso) DO UPDATE SET "
            + "especificidade_rank = excluded.especificidade_rank, "
            + "disponivel = excluded.disponivel",
            nativeQuery = true)
    void upsert(@Param("recursoId") UUID recursoId,
                @Param("codigoRecurso") String codigoRecurso,
                @Param("especificidadeRank") int especificidadeRank,
                @Param("disponivel") boolean disponivel);

    // Le de volta o estado efetivamente persistido logo apos o upsert nativo
    // acima -- e assim que RecursoRepositorioAdapter descobre o recurso_id
    // real (preservado ou recem-gerado) para devolver a UpsertRecurso, que
    // por sua vez decide criado (201) vs atualizado (200) comparando esse
    // valor com o candidato que enviou (ver javadoc de UpsertRecurso).
    Optional<RecursoJpaEntity> findByCodigoRecurso(String codigoRecurso);

    // Conta quantos tiers ESTRITAMENTE mais genericos que :rank tem pelo
    // menos 1 Recurso disponivel (Story 3.2b3, algoritmo de tiers de
    // ConsultarSugestaoRecurso) -- COUNT(DISTINCT especificidade_rank),
    // nunca COUNT(*): Recursos do mesmo tier consomem 1 posicao no total,
    // nunca uma por Recurso (Boundaries "Always" da spec 3.2b3).
    // findById(UUID) usado para buscar o Recurso pelo path {id} ja vem
    // herdado de JpaRepository -- nao precisa de metodo novo aqui (Code Map
    // da spec 3.2b3).
    @Query(value = "SELECT COUNT(DISTINCT especificidade_rank) FROM matching_alocacao.recurso "
            + "WHERE disponivel = true AND especificidade_rank < :rank",
            nativeQuery = true)
    long contarTiersMaisGenericosDisponiveis(@Param("rank") int rank);
}
