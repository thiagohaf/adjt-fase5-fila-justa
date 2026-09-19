package com.filajusta.agendamento.infrastructure.persistence;

import com.filajusta.agendamento.domain.StatusAgendamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

interface AgendamentoJpaRepository extends JpaRepository<AgendamentoJpaEntity, Long> {

    // AD-4 (spec 1.2): FOR UPDATE SKIP LOCKED -- mesmo raciocinio de
    // EventoOutboxJpaRepository#buscarPendentesParaAtualizar. So protege de
    // fato contra corrida entre instancias do poller (2+ tasks ECS
    // concorrentes, I/O Matrix) quando chamada dentro da MESMA transacao que
    // tambem executa atualizarStatusSeAtual em seguida
    // (AbrirJanelaDeConfirmacao.abrirJanelas e @Transactional).
    @Query(value = "SELECT * FROM agendamento_confirmacao.agendamentos "
            + "WHERE status = 'AGUARDANDO_JANELA' AND janela_abre_em <= :agora "
            + "ORDER BY id ASC "
            + "LIMIT :limite "
            + "FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<AgendamentoJpaEntity> buscarPendentesAberturaJanela(@Param("agora") Instant agora,
                                                              @Param("limite") int limite);

    // Escrita condicional (AD-4): retorna 0 quando o Agendamento ja nao
    // estava mais em statusEsperado (reprocessamento do mesmo Agendamento,
    // I/O Matrix -- idempotencia por design, nao um erro).
    @Modifying
    @Query("UPDATE AgendamentoJpaEntity a SET a.status = :novoStatus "
            + "WHERE a.id = :id AND a.status = :statusEsperado")
    int atualizarStatusSeAtual(@Param("id") Long id,
                                @Param("statusEsperado") StatusAgendamento statusEsperado,
                                @Param("novoStatus") StatusAgendamento novoStatus);
}
