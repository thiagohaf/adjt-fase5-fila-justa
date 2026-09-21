package com.confirmasus.agendamento.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

interface EventoOutboxJpaRepository extends JpaRepository<EventoOutboxJpaEntity, Long> {

    // Mesmo padrao do homonimo em matching-alocacao-service: a I/O Matrix da
    // spec 1.2 exige SELECT...FOR UPDATE SKIP LOCKED para o cenario
    // "Multiplas tasks ECS concorrentes" -- um SELECT simples so protegeria a
    // MARCACAO (marcarPublicado, abaixo), nao a LEITURA (duas instancias
    // leriam a mesma linha pendente antes de qualquer uma marcar, publicando-a
    // duas vezes). SKIP LOCKED faz a segunda instancia concorrente pular
    // silenciosamente as linhas ja bloqueadas pela primeira. So funciona
    // porque RelaySnsPublisherJob.publicarPendentes() e @Transactional: o
    // lock desta SELECT so e liberado quando aquele metodo inteiro retorna
    // (depois de publicar e marcar), nunca antes.
    @Query(value = "SELECT * FROM agendamento_confirmacao.eventos_outbox "
            + "WHERE publicado_em IS NULL "
            + "ORDER BY id ASC "
            + "LIMIT :limite "
            + "FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<EventoOutboxJpaEntity> buscarPendentesParaAtualizar(@Param("limite") int limite);

    // "UPDATE ... SET publicado_em = now() WHERE id = ? AND publicado_em IS
    // NULL" -- guarda contra corrida entre instancias do job: retorna 0
    // quando outra instancia ja publicou esta linha primeiro (nao e um erro,
    // ver EventoOutboxRepositorio#marcarComoPublicado).
    @Modifying
    @Query("UPDATE EventoOutboxJpaEntity e SET e.publicadoEm = :publicadoEm "
            + "WHERE e.id = :id AND e.publicadoEm IS NULL")
    int marcarPublicado(@Param("id") Long id, @Param("publicadoEm") Instant publicadoEm);
}
