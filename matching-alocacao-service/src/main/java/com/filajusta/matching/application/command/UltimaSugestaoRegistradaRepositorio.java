package com.filajusta.matching.application.command;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saída de apoio ao rastreamento AD-10 (evento {@code
 * SugestaoGerada} só quando a sugestão de um Recurso muda), Story 3-3c2b1.
 * Guarda qual foi o último {@code pacienteId} sugerido para cada Recurso.
 * Implementada em {@code infrastructure.persistence} (JPA nativo, schema
 * {@code matching_alocacao}, tabela {@code ultima_sugestao_registrada}).
 *
 * <p>Infraestrutura pura, sem nenhum consumidor real ainda -- mesmo padrão
 * da Story 3-3a para o outbox: pré-requisito puro, consumido pela próxima
 * sub-story (3-3c2b2), que aplica o rastreamento em {@code
 * ConsultarSugestaoRecurso}.
 *
 * <p>{@link #registrar(UUID, long, Instant)} é upsert idempotente pela PK
 * {@code recurso_id} -- registrar de novo o mesmo Recurso apenas atualiza
 * {@code paciente_id}/{@code registrado_em}, mesmo mecanismo de upsert
 * nativo de {@link SugestaoRecusadaRepositorio#registrar}, mas com semântica
 * diferente: PK simples aqui (uma única linha por Recurso, sempre
 * sobrescrita), contra a PK composta {@code (recurso_id, paciente_id)} de
 * {@code sugestao_recusada}, onde vários Pacientes recusados coexistem para
 * o mesmo Recurso.
 *
 * <p>Desde a Story 3-3c2b2, {@link #registrar} é um compare-and-set atômico
 * no próprio SQL ({@code WHERE paciente_id <> excluded.paciente_id} no
 * upsert nativo, ver {@code UltimaSugestaoRegistradaJpaRepository}): retorna
 * {@code true} somente quando a linha foi de fato inserida/alterada (não
 * havia registro, OU havia com {@code pacienteId} diferente), {@code false}
 * quando o valor já era o mesmo (nenhuma escrita ocorre). Isso fecha, sem
 * pré-leitura nem lock explícito, a corrida de 2 requisições concorrentes
 * lendo o mesmo valor antigo e publicando 2 eventos {@code SugestaoGerada}
 * duplicados -- {@link com.filajusta.matching.application.query.ConsultarSugestaoRecurso}
 * chama {@link #registrar} direto, sem consultar {@link #pacienteIdRegistrado}
 * antes.
 */
public interface UltimaSugestaoRegistradaRepositorio {

    Optional<Long> pacienteIdRegistrado(UUID recursoId);

    /**
     * @return {@code true} quando a linha foi inserida ou teve {@code
     *     pacienteId} efetivamente alterado; {@code false} quando o valor já
     *     era o mesmo (nenhuma escrita ocorreu).
     */
    boolean registrar(UUID recursoId, long pacienteId, Instant registradoEm);
}
