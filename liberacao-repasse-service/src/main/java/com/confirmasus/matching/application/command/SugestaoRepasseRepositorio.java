package com.confirmasus.matching.application.command;

import com.confirmasus.matching.domain.SugestaoRepasse;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saída de {@link SugestaoRepasse} (Story 6.1, AD-6). Todas as
 * transições são escritas condicionais -- o retorno {@code boolean} diz se
 * este chamador foi o vencedor.
 */
public interface SugestaoRepasseRepositorio {

    /**
     * {@code INSERT ... ON CONFLICT (agendamento_id) DO NOTHING}: {@code
     * false} quando a Vaga já tem sugestão (mensagem duplicada/redrive).
     */
    boolean criarSeAusente(SugestaoRepasse sugestao);

    Optional<SugestaoRepasse> buscarPorId(UUID sugestaoId);

    /** {@code UPDATE ... WHERE status = 'PENDENTE'}: {@code false} = perdeu a corrida. */
    boolean confirmar(UUID sugestaoId, Instant decididoEm);

    /**
     * Reatribui a sugestão ao próximo candidato ({@code proximoPacienteId}
     * {@code null} = Lista esgotada) só se ainda {@code PENDENTE} para o
     * {@code pacienteAtual} recusado: {@code false} = perdeu a corrida.
     */
    boolean reatribuir(UUID sugestaoId, long pacienteAtual, Long proximoPacienteId, Instant decididoEm);
}
