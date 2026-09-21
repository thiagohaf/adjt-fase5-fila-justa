package com.confirmasus.auditoria.domain;

/**
 * Enumeração das decisões auditáveis do sistema.
 * Cada tipo mapeia para um evento específico do domínio.
 *
 * <p>Tipos:
 * <ul>
 *   <li>{@code NOTIFICACAO} - NotificacaoConfirmacaoPublicada (motivo = null)
 *   <li>{@code CONFIRMACAO} - ConfirmacaoRegistrada (motivo = null)
 *   <li>{@code RECUSA} - RecusaRegistrada (motivo preenchido)
 *   <li>{@code NAO_CONFIRMADO} - AgendamentoNaoConfirmado (motivo preenchido)
 *   <li>{@code LIBERACAO} - VagaLiberada (motivo preenchido)
 *   <li>{@code SUGESTAO_GERADA} - SugestaoRepasseGerada (motivo = null)
 *   <li>{@code REPASSE_CONFIRMADO} - RepasseConfirmado (motivo preenchido)
 *   <li>{@code SUGESTAO_RECUSADA} - SugestaoRepasseRecusada (motivo preenchido)
 *   <li>{@code GENERICO} - Eventos desconhecidos (motivo = null, payload bruto)
 * </ul>
 */
public enum TipoDecisao {
    NOTIFICACAO,
    CONFIRMACAO,
    RECUSA,
    NAO_CONFIRMADO,
    LIBERACAO,
    SUGESTAO_GERADA,
    REPASSE_CONFIRMADO,
    SUGESTAO_RECUSADA,
    GENERICO
}
