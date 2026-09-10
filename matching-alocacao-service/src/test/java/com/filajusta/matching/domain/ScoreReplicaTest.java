package com.filajusta.matching.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cobre {@link ScoreReplica#maisRecenteQue(ScoreReplica)} -- a regra pura de
 * last-write-wins com desempate lexicográfico por {@code eventId}
 * (Boundaries da spec 3.1b). Cobre a I/O &amp; Edge-Case Matrix inteira sem
 * banco (Tasks da spec: "Teste unitário do upsert -- cobre a I/O Matrix").
 */
class ScoreReplicaTest {

    private static final long PACIENTE_ID = 42L;
    private static final Instant T1 = Instant.parse("2026-09-08T12:00:00Z");
    private static final Instant T2 = Instant.parse("2026-09-08T13:00:00Z");

    private static ScoreReplica replica(int score, Instant occurredAt, UUID eventId) {
        return new ScoreReplica(PACIENTE_ID, score, occurredAt, eventId, Instant.now());
    }

    @Test
    void construtorValidaPacienteIdPositivo() {
        assertThatThrownBy(() -> new ScoreReplica(0, 50, T1, UUID.randomUUID(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScoreReplica(-1, 50, T1, UUID.randomUUID(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construtorValidaScoreEntre0E100() {
        assertThatThrownBy(() -> new ScoreReplica(PACIENTE_ID, -1, T1, UUID.randomUUID(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScoreReplica(PACIENTE_ID, 101, T1, UUID.randomUUID(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construtorRejeitaCamposNulos() {
        assertThatThrownBy(() -> new ScoreReplica(PACIENTE_ID, 50, null, UUID.randomUUID(), Instant.now()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ScoreReplica(PACIENTE_ID, 50, T1, null, Instant.now()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ScoreReplica(PACIENTE_ID, 50, T1, UUID.randomUUID(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void semReplicaAtualSempreEMaisRecente() {
        ScoreReplica candidata = replica(50, T1, UUID.randomUUID());

        assertThat(candidata.maisRecenteQue(null)).isTrue();
    }

    @Test
    void consumoNormalOccurredAtMaiorEMaisRecente() {
        ScoreReplica atual = replica(50, T1, UUID.randomUUID());
        ScoreReplica candidata = replica(70, T2, UUID.randomUUID());

        assertThat(candidata.maisRecenteQue(atual)).isTrue();
    }

    @Test
    void mensagemForaDeOrdemOccurredAtMenorNuncaEMaisRecente() {
        // I/O Matrix: "occurredAt menor que o ja persistido -- replica
        // mantem a versao mais recente; mensagem antiga nao sobrescreve".
        ScoreReplica atual = replica(70, T2, UUID.randomUUID());
        ScoreReplica candidataAntiga = replica(50, T1, UUID.randomUUID());

        assertThat(candidataAntiga.maisRecenteQue(atual)).isFalse();
    }

    @Test
    void redeliveryDoMesmoEventIdNuncaEMaisRecenteQueSiMesma() {
        // I/O Matrix: "mesmo eventId entregue duas vezes -- upsert
        // idempotente, replica nao duplica nem retrocede".
        UUID eventId = UUID.randomUUID();
        ScoreReplica atual = replica(70, T1, eventId);
        ScoreReplica redelivery = replica(70, T1, eventId);

        assertThat(redelivery.maisRecenteQue(atual)).isFalse();
    }

    @Test
    void empateDeOccurredAtDesempataPorOrdemLexicograficaMesmoQuandoDiscordaDeUuidCompareTo() {
        // Achado do code review: os UUIDs incrementais triviais dos demais
        // testes de desempate (...0001 vs ...0002) nunca provam a afirmacao
        // do javadoc de ScoreReplica#maisRecenteQue -- nesses casos a ordem
        // lexicografica da string e UUID#compareTo(UUID) sempre concordam.
        // UUID#compareTo compara mostSigBits/leastSigBits como long
        // ASSINADO: um UUID cujo primeiro hex digit e 8..f tem o bit mais
        // significativo de mostSigBits ligado, armazenado como long
        // NEGATIVO -- inverte o sinal da comparacao em relacao a ordem
        // lexicografica da forma textual. Verificado empiricamente (jshell):
        // "80000000-...".compareTo("70000000-...") (String) == 1, mas
        // UUID.fromString("80000000-...").compareTo(UUID.fromString("70000000-..."))
        // == -1 -- os dois discordam nesse par especifico.
        UUID eventIdMenorPrefixo7 = UUID.fromString("70000000-0000-0000-0000-000000000000");
        UUID eventIdMaiorPrefixo8 = UUID.fromString("80000000-0000-0000-0000-000000000000");
        assertThat(eventIdMaiorPrefixo8.toString().compareTo(eventIdMenorPrefixo7.toString())).isPositive();
        assertThat(eventIdMaiorPrefixo8.compareTo(eventIdMenorPrefixo7))
                .as("par escolhido de proposito para UUID#compareTo discordar da ordem lexicografica da string")
                .isNegative();

        ScoreReplica atual = replica(50, T1, eventIdMenorPrefixo7);
        ScoreReplica candidataLexicograficamenteMaior = replica(60, T1, eventIdMaiorPrefixo8);

        // maisRecenteQue segue a ordem lexicografica da string (igual ao
        // Postgres nativo) -- se usasse UUID#compareTo ingenuamente,
        // retornaria false aqui.
        assertThat(candidataLexicograficamenteMaior.maisRecenteQue(atual)).isTrue();
    }

    @Test
    void empateDeOccurredAtDesempataPorEventIdLexicograficamenteMaior() {
        ScoreReplica atual = replica(50, T1,
                UUID.fromString("00000000-0000-0000-0000-000000000001"));
        ScoreReplica candidataMaior = replica(60, T1,
                UUID.fromString("00000000-0000-0000-0000-000000000002"));

        assertThat(candidataMaior.maisRecenteQue(atual)).isTrue();
    }

    @Test
    void empateDeOccurredAtComEventIdLexicograficamenteMenorNaoSubstitui() {
        ScoreReplica atual = replica(50, T1,
                UUID.fromString("00000000-0000-0000-0000-000000000002"));
        ScoreReplica candidataMenor = replica(60, T1,
                UUID.fromString("00000000-0000-0000-0000-000000000001"));

        assertThat(candidataMenor.maisRecenteQue(atual)).isFalse();
    }

    @Test
    void comparacaoEntrePacientesDiferentesLancaExcecao() {
        ScoreReplica pacienteA = new ScoreReplica(1L, 50, T1, UUID.randomUUID(), Instant.now());
        ScoreReplica pacienteB = new ScoreReplica(2L, 50, T1, UUID.randomUUID(), Instant.now());

        assertThatThrownBy(() -> pacienteB.maisRecenteQue(pacienteA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void gettersExpoemOsCamposConstruidos() {
        Instant atualizadoEm = Instant.parse("2026-09-08T14:00:00Z");
        UUID eventId = UUID.randomUUID();
        ScoreReplica r = new ScoreReplica(PACIENTE_ID, 88, T1, eventId, atualizadoEm);

        assertThat(r.getPacienteId()).isEqualTo(PACIENTE_ID);
        assertThat(r.getScore()).isEqualTo(88);
        assertThat(r.getOccurredAt()).isEqualTo(T1);
        assertThat(r.getEventId()).isEqualTo(eventId);
        assertThat(r.getUpdatedAt()).isEqualTo(atualizadoEm);
    }
}
