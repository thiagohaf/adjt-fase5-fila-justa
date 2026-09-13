package com.filajusta.matching.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface LiberacaoAgendadaJpaRepository extends JpaRepository<LiberacaoAgendadaJpaEntity, UUID> {

    // Mesmo padrao de EventoOutboxJpaRepository#buscarPendentesParaAtualizar
    // (Story 3-3a): SELECT...FOR UPDATE SKIP LOCKED protege a LEITURA contra
    // duas instancias do futuro relay (Story 3-4a2) publicando a mesma linha
    // duas vezes -- so funciona porque o chamador roda dentro de uma
    // transacao que tambem marca a linha como enviada antes de retornar.
    @Query(value = "SELECT * FROM matching_alocacao.liberacao_agendada "
            + "WHERE enviado_em IS NULL "
            + "ORDER BY criado_em ASC "
            + "LIMIT :limite "
            + "FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<LiberacaoAgendadaJpaEntity> buscarPendentesParaAtualizar(@Param("limite") int limite);

    // "UPDATE ... SET enviado_em = now() WHERE alocacao_id = ? AND
    // enviado_em IS NULL" -- guarda contra corrida entre instancias do
    // futuro relay: retorna 0 quando outra instancia ja marcou esta linha
    // primeiro (nao e um erro).
    @Modifying
    @Query("UPDATE LiberacaoAgendadaJpaEntity l SET l.enviadoEm = :enviadoEm "
            + "WHERE l.alocacaoId = :alocacaoId AND l.enviadoEm IS NULL")
    int marcarEnviado(@Param("alocacaoId") UUID alocacaoId, @Param("enviadoEm") Instant enviadoEm);
}
