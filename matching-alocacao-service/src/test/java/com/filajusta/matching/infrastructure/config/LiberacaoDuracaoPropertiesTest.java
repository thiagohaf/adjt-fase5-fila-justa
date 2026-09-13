package com.filajusta.matching.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cobre {@link LiberacaoDuracaoProperties} (Story 3-4a1) -- a I/O
 * &amp; Edge-Case Matrix da spec: rank sem duração configurada precisa
 * falhar fail-fast no boot do bean (aqui, no próprio construtor), nunca
 * silenciosamente em runtime por Recurso; e o limite físico de 15min do
 * {@code DelaySeconds} do SQS (Boundaries da spec 3-4a1), respeitado já
 * nesta story mesmo sem publicar nada ainda.
 */
class LiberacaoDuracaoPropertiesTest {

    @Test
    void duracaoParaRankDevolveAExataDuracaoConfiguradaParaCadaRank() {
        LiberacaoDuracaoProperties properties = new LiberacaoDuracaoProperties(
                Duration.ofMinutes(2), Duration.ofMinutes(4), Duration.ofMinutes(6), Duration.ofMinutes(8));

        assertThat(properties.duracaoParaRank(1)).isEqualTo(Duration.ofMinutes(2));
        assertThat(properties.duracaoParaRank(2)).isEqualTo(Duration.ofMinutes(4));
        assertThat(properties.duracaoParaRank(3)).isEqualTo(Duration.ofMinutes(6));
        assertThat(properties.duracaoParaRank(4)).isEqualTo(Duration.ofMinutes(8));
    }

    @Test
    void rankSemEntradaNoConstrutorLancaFailFastAoInvesDeFalharSilenciosamenteEmRuntime() {
        // Simula "filajusta.liberacao.duracao.rank-2 nao configurado" --
        // MatchingAlocacaoServiceApplication@Value ja falharia antes disso
        // (placeholder nao resolvido), mas a validacao aqui e a rede de
        // seguranca caso o bean seja montado de outra forma (ex. teste).
        assertThatThrownBy(() -> new LiberacaoDuracaoProperties(
                Duration.ofMinutes(2), null, Duration.ofMinutes(6), Duration.ofMinutes(8)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("rank-2");
    }

    @Test
    void duracaoAcimaDoLimiteFisicoDoDelaySecondsDoSqsLancaFailFast() {
        assertThatThrownBy(() -> new LiberacaoDuracaoProperties(
                Duration.ofMinutes(2), Duration.ofMinutes(4), Duration.ofMinutes(6), Duration.ofMinutes(16)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rank-4")
                .hasMessageContaining("15min");
    }

    @Test
    void duracaoZeroOuNegativaLancaFailFast() {
        assertThatThrownBy(() -> new LiberacaoDuracaoProperties(
                Duration.ZERO, Duration.ofMinutes(4), Duration.ofMinutes(6), Duration.ofMinutes(8)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rank-1");
    }

    @Test
    void duracaoPositivaSubSegundoLancaFailFastNoBootAoInvesDeTruncarParaZeroDelaySegundos() {
        // Achado do code review multi-agente da spec 3-4a1: PT0.5S nao e
        // isZero() nem isNegative(), mas Duration#toSeconds() trunca para 0
        // -- sem esta validacao, o boot passaria e so estouraria
        // IllegalArgumentException (delaySegundos <= 0) dentro da transacao
        // de ConfirmarAlocacao, na primeira confirmacao daquele rank.
        assertThatThrownBy(() -> new LiberacaoDuracaoProperties(
                Duration.ofMillis(500), Duration.ofMinutes(4), Duration.ofMinutes(6), Duration.ofMinutes(8)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rank-1");
    }

    @Test
    void duracaoParaRankForaDoIntervaloConhecidoLancaExcecaoClara() {
        LiberacaoDuracaoProperties properties = new LiberacaoDuracaoProperties(
                Duration.ofMinutes(2), Duration.ofMinutes(4), Duration.ofMinutes(6), Duration.ofMinutes(8));

        assertThatThrownBy(() -> properties.duracaoParaRank(5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("5");
    }
}
