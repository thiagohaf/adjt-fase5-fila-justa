package com.filajusta.matching.infrastructure.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Calibra o delay de {@code LiberacaoAgendada} (Story 3-4a1) por
 * {@code especificidadeRank} do {@code Recurso} -- fonte única, montada em
 * {@code MatchingAlocacaoServiceApplication} a partir de
 * {@code filajusta.liberacao.duracao.rank-{1..4}} (formato {@code Duration},
 * ex. {@code PT2M}), mesmo padrão de {@code @Value} usado por
 * {@link com.filajusta.matching.domain.PrioridadeEfetiva} (
 * {@code filajusta.aging.k}/{@code filajusta.aging.teto}, application.yml)
 * -- não um {@code @ConfigurationProperties} com {@code Map} porque
 * bind de Map raiz de prefixo (sem segmento de campo extra) exigiria um
 * truque de binding não usado em nenhum outro lugar deste projeto; 4
 * {@code @Value} simples (um por rank, únicos valores concretos que
 * {@code Recurso.especificidadeRank} assume hoje -- seed do Epic 5) é mais
 * simples e igualmente fail-fast.
 *
 * <p>{@code especificidadeRank} só assume 1-4 hoje (javadoc de
 * {@code Recurso}) -- validar aqui, no construtor deste bean, que as 4
 * durações estão configuradas e dentro do limite garante que nenhum
 * {@code Recurso} desses ranks jamais encontre um "buraco" de config em
 * runtime (I/O Matrix da spec 3-4a1: falha de startup, não silenciosa por
 * Recurso). Se {@code @Value} sozinho já falha o boot quando a property
 * está ausente (placeholder não resolvido), a validação extra aqui cobre
 * positividade e o limite físico do {@code DelaySeconds} do SQS (15min,
 * Boundaries da spec 3-4a1) -- respeitado já nesta story, mesmo sem
 * publicar nada ainda, para não exigir revalidação em 3-4a2.
 */
public final class LiberacaoDuracaoProperties {

    private static final Duration LIMITE_SQS_DELAY = Duration.ofMinutes(15);

    private final Map<Integer, Duration> duracaoPorRank;

    public LiberacaoDuracaoProperties(Duration rank1, Duration rank2, Duration rank3, Duration rank4) {
        Map<Integer, Duration> resolvido = new LinkedHashMap<>();
        resolvido.put(1, validar(1, rank1));
        resolvido.put(2, validar(2, rank2));
        resolvido.put(3, validar(3, rank3));
        resolvido.put(4, validar(4, rank4));
        this.duracaoPorRank = Map.copyOf(resolvido);
    }

    private static Duration validar(int rank, Duration duracao) {
        Objects.requireNonNull(duracao,
                () -> "filajusta.liberacao.duracao.rank-" + rank + " nao configurado");
        // toSeconds() < 1 (nao so isZero()/isNegative()) -- achado do code
        // review multi-agente da spec 3-4a1: uma duracao positiva
        // sub-segundo (ex. PT0.5S) passaria por isZero()/isNegative(), mas
        // truncaria para 0 em ConfirmarAlocacao#confirmar
        // (duracaoLiberacao.toSeconds()), estourando IllegalArgumentException
        // do construtor de LiberacaoAgendada (delaySegundos <= 0) em tempo de
        // CONFIRMACAO -- nao no BOOT como a spec exige (I/O Matrix: "erro
        // claro na inicializacao do bean de config (fail-fast)... Falha de
        // startup, nao silenciosa em runtime por Recurso").
        if (duracao.toSeconds() < 1) {
            throw new IllegalStateException(
                    "filajusta.liberacao.duracao.rank-" + rank + " deve ser positivo: " + duracao);
        }
        if (duracao.compareTo(LIMITE_SQS_DELAY) > 0) {
            throw new IllegalStateException(
                    "filajusta.liberacao.duracao.rank-" + rank
                            + " excede o limite de 15min do DelaySeconds do SQS: " + duracao);
        }
        return duracao;
    }

    /**
     * Duração configurada para {@code especificidadeRank} -- lançar aqui
     * (em vez de devolver {@code Optional}/{@code null}) é seguro porque o
     * construtor já garante as 4 chaves 1-4 no boot; um rank fora desse
     * intervalo indicaria um {@code Recurso} inconsistente com o próprio
     * domínio ({@code Recurso.especificidadeRank} não vai além do seed do
     * Epic 5), nunca uma lacuna de configuração.
     */
    public Duration duracaoParaRank(int especificidadeRank) {
        Duration duracao = duracaoPorRank.get(especificidadeRank);
        if (duracao == null) {
            throw new IllegalStateException(
                    "especificidadeRank " + especificidadeRank
                            + " sem duracao configurada em filajusta.liberacao.duracao");
        }
        return duracao;
    }
}
