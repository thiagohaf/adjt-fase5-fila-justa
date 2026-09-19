package com.filajusta.matching.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

interface UltimaSugestaoRegistradaJpaRepository extends JpaRepository<UltimaSugestaoRegistradaJpaEntity, UUID> {

    // Leitura herdada via findById(UUID) cobre a I/O Matrix (Optional.empty()
    // quando nao ha registro para o recursoId, nunca excecao) -- nao precisa
    // de query nativa propria.

    // Upsert direto e idempotente pela PK simples recurso_id (Boundaries
    // "Always" da spec 3-3c2b1) -- mesmo estilo do upsert de
    // SugestaoRecusadaJpaRepository#upsert. Evita a corrida de findById +
    // save em duas etapas.
    //
    // Desde a Story 3-3c2b2: o DO UPDATE ganhou a clausula WHERE condicional
    // abaixo -- compare-and-set atomico no proprio SQL, fechando a corrida
    // de escrita concorrente identificada em revisao de codigo: 2
    // requisicoes simultaneas que leem o mesmo pacienteIdRegistrado antigo e
    // calculam a mesma nova sugestao publicariam 2 eventos SugestaoGerada
    // duplicados sem essa clausula. Quando o paciente_id ja persistido e
    // igual ao novo valor, a condicao WHERE e falsa e o Postgres NAO conta a
    // linha como afetada -- @Modifying devolve 0 nesse caso, 1 quando insere
    // (sem conflito) ou quando o UPDATE realmente aplica (conflito com
    // paciente_id diferente). Atomicidade sob concorrencia real provada em
    // UltimaSugestaoRegistradaRepositorioAdapterIntegrationTest#registrosConcorrentesParaOMesmoValorNovoApenasUmDelesRetornaTrue
    // (N threads via ExecutorService/CountDownLatch, nao so chamadas
    // sequenciais).
    @Modifying
    @Query(value = "INSERT INTO matching_alocacao.ultima_sugestao_registrada "
            + "(recurso_id, paciente_id, registrado_em) "
            + "VALUES (:recursoId, :pacienteId, :registradoEm) "
            + "ON CONFLICT (recurso_id) DO UPDATE SET "
            + "paciente_id = excluded.paciente_id, "
            + "registrado_em = excluded.registrado_em "
            + "WHERE ultima_sugestao_registrada.paciente_id <> excluded.paciente_id",
            nativeQuery = true)
    int upsert(@Param("recursoId") UUID recursoId,
               @Param("pacienteId") long pacienteId,
               @Param("registradoEm") Instant registradoEm);
}
