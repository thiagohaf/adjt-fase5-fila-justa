package com.filajusta.matching.infrastructure.relay;

import com.filajusta.matching.application.command.EventoOutboxRepositorio;
import com.filajusta.matching.domain.EventoOutbox;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link RelaySnsPublisherJob} com {@link SnsClient} mockado --
 * equivalente a {@code triagem-score-service/.../relay/
 * RelaySnsPublisherJobTest.java} (Story 3.0), adaptado para
 * {@code recursoId}/{@code MessageGroupId} deste servico (AD-3, Boundaries
 * da spec 3-3a).
 *
 * <p>Existe especificamente para exercitar a 3a Acceptance Criteria da spec
 * 3-3a: "Given uma falha de rede simulada antes do ack do SNS, when o job
 * roda de novo, then o mesmo eventId e reusado (nunca regenerado) -- sem
 * duplicacao nem perda". O cenario
 * {@link #falhaTransitoriaDoSnsNaoPropagaExcecaoENaoMarcaALinhaComoPublicada()}
 * prova isso por construcao: quando {@code snsClient.publish(...)} lanca,
 * {@code repositorio.marcarComoPublicado(...)} nunca e chamado -- a linha
 * permanece com {@code publicado_em IS NULL} e {@code eventId} inalterado
 * (ele e lido do banco, nunca gerado no relay), entao uma nova execucao do
 * job reencontraria a MESMA linha com o MESMO eventId via
 * {@link EventoOutboxRepositorio#buscarNaoPublicados(int)}.
 *
 * <p>Achado do code review multi-agente da emenda de provisionamento do
 * topico SNS (Story 3-3a): os 4 cenarios abaixo existiam no homonimo de
 * {@code triagem-score-service} mas nao tinham equivalente aqui, apesar de
 * este job implementar exatamente o mesmo comportamento --
 * {@link #falhaNaSegundaLinhaInterrompeOLotePreservandoOrdemFifoDoRecurso()},
 * {@link #falhaDeUmRecursoNaoBloqueiaAPublicacaoDeOutrosRecursosNoMesmoLote()},
 * {@link #messageGroupIdAusenteNoPayloadFalhaAPublicacaoSemChamarSnsNemMarcar()}
 * e {@link #batchSizeMenorOuIgualAZeroEClampeadoParaUm()}. O percurso ponta a
 * ponta contra SNS/SQS reais (LocalStack) vive em
 * {@code RelaySnsPublisherJobIntegrationTest}.
 */
class RelaySnsPublisherJobTest {

    private static final String TOPIC_ARN = "arn:aws:sns:us-east-1:000000000000:matching-alocacao.fifo";

    private final EventoOutboxRepositorio repositorio = mock(EventoOutboxRepositorio.class);
    private final SnsClient snsClient = mock(SnsClient.class);
    private final tools.jackson.databind.ObjectMapper objectMapper = new tools.jackson.databind.ObjectMapper();

    private RelaySnsPublisherJob job(int loteTamanho) {
        return new RelaySnsPublisherJob(repositorio, snsClient, objectMapper, TOPIC_ARN, loteTamanho);
    }

    private static EventoOutbox evento(long id, long recursoId) {
        return new EventoOutbox(id, UUID.randomUUID(), "AlocacaoConfirmada", Instant.parse("2026-09-08T12:00:00Z"),
                1, "corr-" + id, Map.of("recursoId", recursoId, "pacienteId", 7L));
    }

    @Test
    void falhaTransitoriaDoSnsNaoPropagaExcecaoENaoMarcaALinhaComoPublicada() {
        // AC 3 da spec 3-3a: falha de rede simulada antes do ack do SNS --
        // o eventId nunca e regenerado (e lido do banco), entao a mesma
        // linha e reencontrada com o mesmo eventId na proxima execucao. A
        // prova por construcao e que marcarComoPublicado nunca e chamado
        // aqui: a linha permanece pendente (publicado_em IS NULL).
        EventoOutbox evento = evento(1L, 42L);
        UUID eventIdOriginal = evento.getEventId();
        when(repositorio.buscarNaoPublicados(50)).thenReturn(List.of(evento));
        when(snsClient.publish(any(PublishRequest.class))).thenThrow(new RuntimeException("SNS indisponivel"));

        assertThatCode(() -> job(50).publicarPendentes()).doesNotThrowAnyException();

        verify(repositorio, never()).marcarComoPublicado(anyLong());

        // "o job roda de novo": buscarNaoPublicados devolveria a MESMA linha,
        // com o MESMO eventId (nunca regenerado) -- nao ha duplicacao (o
        // MessageDeduplicationId do SNS seria o mesmo) nem perda (a linha
        // nunca desapareceu do outbox).
        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(captor.capture());
        assertThat(captor.getValue().messageDeduplicationId()).isEqualTo(eventIdOriginal.toString());
    }

    @Test
    void nenhumaLinhaPendenteNaoChamaSnsNemMarcaNada() {
        when(repositorio.buscarNaoPublicados(50)).thenReturn(List.of());

        job(50).publicarPendentes();

        verifyNoInteractions(snsClient);
        verify(repositorio, never()).marcarComoPublicado(anyLong());
    }

    @Test
    void topicArnVazioComRelayHabilitadoFalhaNaConstrucaoDoJob() {
        // Achado do code review (fail-fast, mesmo de triagem-score-service):
        // sem isso, o relay habilitado com topic-arn vazio rodaria para
        // sempre falhando em silencio a cada ciclo, em vez de derrubar a
        // subida do servico.
        assertThatThrownBy(() -> new RelaySnsPublisherJob(repositorio, snsClient, objectMapper, "", 50))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new RelaySnsPublisherJob(repositorio, snsClient, objectMapper, "   ", 50))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new RelaySnsPublisherJob(repositorio, snsClient, objectMapper, null, 50))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void falhaNaSegundaLinhaInterrompeOLotePreservandoOrdemFifoDoRecurso() {
        // Equivalente a triagem-score-service (adaptado para recursoId):
        // a terceira linha (mesmo recursoId da segunda, que falhou) nunca e
        // tentada nesta execucao -- publica-la fora de ordem violaria o
        // FIFO por MessageGroupId=recursoId.
        EventoOutbox primeiro = evento(1L, 42L);
        EventoOutbox segundo = evento(2L, 42L);
        EventoOutbox terceiro = evento(3L, 42L);
        when(repositorio.buscarNaoPublicados(50)).thenReturn(List.of(primeiro, segundo, terceiro));
        when(repositorio.marcarComoPublicado(1L)).thenReturn(true);
        when(snsClient.publish(any(PublishRequest.class)))
                .thenReturn(PublishResponse.builder().build())
                .thenThrow(new RuntimeException("SNS indisponivel"));

        job(50).publicarPendentes();

        verify(snsClient, times(2)).publish(any(PublishRequest.class));
        verify(repositorio).marcarComoPublicado(1L);
        verify(repositorio, never()).marcarComoPublicado(2L);
        verify(repositorio, never()).marcarComoPublicado(3L);
    }

    @Test
    void falhaDeUmRecursoNaoBloqueiaAPublicacaoDeOutrosRecursosNoMesmoLote() {
        // Equivalente a triagem-score-service (adaptado para recursoId,
        // head-of-line blocking): so eventos SEGUINTES do MESMO recursoId da
        // linha que falhou devem ser pulados -- um recurso com falha
        // permanente nao pode travar o backlog inteiro de todos os outros
        // recursos no mesmo ciclo.
        EventoOutbox recursoAPrimeiro = evento(1L, 42L);
        EventoOutbox recursoASegundo = evento(2L, 42L);
        EventoOutbox recursoB = evento(3L, 43L);
        when(repositorio.buscarNaoPublicados(50))
                .thenReturn(List.of(recursoAPrimeiro, recursoASegundo, recursoB));
        when(repositorio.marcarComoPublicado(3L)).thenReturn(true);
        when(snsClient.publish(any(PublishRequest.class)))
                .thenThrow(new RuntimeException("SNS indisponivel para o recurso 42"))
                .thenReturn(PublishResponse.builder().build());

        job(50).publicarPendentes();

        // Recurso 42: primeiro evento falha, segundo e pulado (preserva FIFO).
        verify(repositorio, never()).marcarComoPublicado(1L);
        verify(repositorio, never()).marcarComoPublicado(2L);
        // Recurso 43: publicado normalmente, sem ser bloqueado pela falha do recurso 42.
        verify(repositorio).marcarComoPublicado(3L);
        verify(snsClient, times(2)).publish(any(PublishRequest.class));
    }

    @Test
    void messageGroupIdAusenteNoPayloadFalhaAPublicacaoSemChamarSnsNemMarcar() {
        // Equivalente a triagem-score-service: String.valueOf(null) geraria
        // a string "null" como MessageGroupId em vez de recusar o evento
        // malformado -- deve falhar alto, tratado como falha de publicacao
        // (mesmo caminho de log/retry da falha de SNS, sem derrubar o job).
        EventoOutbox semRecursoId = new EventoOutbox(1L, UUID.randomUUID(), "AlocacaoConfirmada",
                Instant.parse("2026-09-08T12:00:00Z"), 1, "corr-1", Map.of("pacienteId", 7L));
        when(repositorio.buscarNaoPublicados(50)).thenReturn(List.of(semRecursoId));

        assertThatCode(() -> job(50).publicarPendentes()).doesNotThrowAnyException();

        verify(snsClient, never()).publish(any(PublishRequest.class));
        verify(repositorio, never()).marcarComoPublicado(anyLong());
    }

    @Test
    void batchSizeMenorOuIgualAZeroEClampeadoParaUm() {
        // Equivalente a triagem-score-service: batch-size <= 0 quebraria a
        // query de leitura (LIMIT invalido) a cada execucao -- pisado em 1
        // em vez de propagar o valor invalido.
        when(repositorio.buscarNaoPublicados(1)).thenReturn(List.of());

        new RelaySnsPublisherJob(repositorio, snsClient, objectMapper, TOPIC_ARN, 0).publicarPendentes();

        verify(repositorio).buscarNaoPublicados(1);
    }
}
