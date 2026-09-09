package com.filajusta.triagem.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

interface EventoOutboxJpaRepository extends JpaRepository<EventoOutboxJpaEntity, Long> {

    // Design Notes da spec 3.0: "poller le WHERE publicado_em IS NULL ORDER
    // BY id" -- "FOR UPDATE SKIP LOCKED" (achado do code review: a I/O
    // Matrix, frozen na spec, exige explicitamente lock otimista OU
    // SELECT...FOR UPDATE SKIP LOCKED para o cenario "Multiplas instancias
    // do job rodando"; um SELECT simples so protege a MARCACAO
    // (marcarPublicado, abaixo), nao a LEITURA -- duas instancias liam a
    // mesma linha pendente antes de qualquer uma marcar, publicando-a duas
    // vezes). SKIP LOCKED faz a segunda instancia concorrente pular
    // silenciosamente as linhas ja bloqueadas pela primeira, escolhendo
    // outras -- em vez de bloquear esperando. So funciona porque
    // RelaySnsPublisherJob.publicarPendentes() e @Transactional: o lock
    // desta SELECT so e liberado quando aquele metodo inteiro retorna
    // (depois de publicar e marcar), nunca antes.
    @Query(value = "SELECT * FROM triagem_score.eventos_outbox "
            + "WHERE publicado_em IS NULL "
            + "ORDER BY id ASC "
            + "LIMIT :limite "
            + "FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<EventoOutboxJpaEntity> buscarPendentesParaAtualizar(@Param("limite") int limite);

    // "UPDATE ... SET publicado_em = now() WHERE id = ? AND publicado_em IS
    // NULL" (Design Notes da spec 3.0) -- guarda contra corrida entre
    // instancias do job: retorna 0 quando outra instancia ja publicou esta
    // linha primeiro (nao e um erro, ver EventoOutboxRepositorio#marcarComoPublicado).
    @Modifying
    @Query("UPDATE EventoOutboxJpaEntity e SET e.publicadoEm = :publicadoEm "
            + "WHERE e.id = :id AND e.publicadoEm IS NULL")
    int marcarPublicado(@Param("id") Long id, @Param("publicadoEm") Instant publicadoEm);

    // Story 3.1a (GET /internal/scores, ListarScoresAtuais): query derivada
    // simples (sem @Query, Code Map da spec pede "leitura simples sem query
    // nova complexa") -- publicado_em NAO entra no filtro, um Score atual
    // continua atual mesmo antes do relay publicar no SNS.
    List<EventoOutboxJpaEntity> findByEventTypeOrderByOccurredAtAsc(String eventType);
}
