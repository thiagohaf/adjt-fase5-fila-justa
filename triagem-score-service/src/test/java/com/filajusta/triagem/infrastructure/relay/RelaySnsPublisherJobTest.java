package com.filajusta.triagem.infrastructure.relay;

import com.filajusta.triagem.application.command.EventoOutboxRepositorio;
import com.filajusta.triagem.domain.EventoOutbox;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
 * Cobre {@link RelaySnsPublisherJob} com {@link SnsClient} mockado (Tasks da
 * spec 3.0: "Testes unitarios do job (mock do cliente SNS) cobrindo a I/O
 * Matrix") -- sem Testcontainers/rede real; o percurso ponta a ponta contra
 * SNS/SQS de verdade (LocalStack) vive em
 * {@code RelaySnsPublisherJobIntegrationTest}.
 */
class RelaySnsPublisherJobTest {

    private static final String TOPIC_ARN = "arn:aws:sns:us-east-1:000000000000:score-calculado.fifo";

    private final EventoOutboxRepositorio repositorio = mock(EventoOutboxRepositorio.class);
    private final SnsClient snsClient = mock(SnsClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private RelaySnsPublisherJob job(int loteTamanho) {
        return new RelaySnsPublisherJob(repositorio, snsClient, objectMapper, TOPIC_ARN, loteTamanho);
    }

    private static EventoOutbox evento(long id, long pacienteId) {
        return new EventoOutbox(id, UUID.randomUUID(), "ScoreCalculado", Instant.parse("2026-09-08T12:00:00Z"),
                1, "corr-" + id, Map.of("pacienteId", pacienteId, "triagemId", 7L));
    }

    @Test
    void publicaLinhaPendenteComOEnvelopeCompletoEMarcaComoPublicada() {
        EventoOutbox evento = evento(1L, 42L);
        when(repositorio.buscarNaoPublicados(50)).thenReturn(List.of(evento));
        when(repositorio.marcarComoPublicado(1L)).thenReturn(true);

        job(50).publicarPendentes();

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(captor.capture());
        PublishRequest request = captor.getValue();

        assertThat(request.topicArn()).isEqualTo(TOPIC_ARN);
        // MessageGroupId = pacienteId (AD-3); MessageDeduplicationId = eventId do outbox (Boundaries da spec 3.0).
        assertThat(request.messageGroupId()).isEqualTo("42");
        assertThat(request.messageDeduplicationId()).isEqualTo(evento.getEventId().toString());

        JsonNode envelope = objectMapper.readTree(request.message());
        assertThat(envelope.get("eventId").asText()).isEqualTo(evento.getEventId().toString());
        assertThat(envelope.get("eventType").asText()).isEqualTo("ScoreCalculado");
        assertThat(envelope.get("occurredAt").asText()).isNotBlank();
        assertThat(envelope.get("version").asInt()).isEqualTo(1);
        assertThat(envelope.get("correlationId").asText()).isEqualTo("corr-1");
        assertThat(envelope.get("payload").get("pacienteId").asLong()).isEqualTo(42L);
        assertThat(envelope.get("payload").get("triagemId").asLong()).isEqualTo(7L);

        verify(repositorio).marcarComoPublicado(1L);
    }

    @Test
    void nenhumaLinhaPendenteNaoChamaSnsNemMarcaNada() {
        when(repositorio.buscarNaoPublicados(50)).thenReturn(List.of());

        job(50).publicarPendentes();

        verifyNoInteractions(snsClient);
        verify(repositorio, never()).marcarComoPublicado(anyLong());
    }

    @Test
    void falhaTransitoriaDoSnsNaoPropagaExcecaoENaoMarcaALinhaComoPublicada() {
        EventoOutbox evento = evento(1L, 42L);
        when(repositorio.buscarNaoPublicados(50)).thenReturn(List.of(evento));
        when(snsClient.publish(any(PublishRequest.class))).thenThrow(new RuntimeException("SNS indisponivel"));

        assertThatCode(() -> job(50).publicarPendentes()).doesNotThrowAnyException();

        verify(repositorio, never()).marcarComoPublicado(anyLong());
    }

    @Test
    void falhaAoLerPendentesDoOutboxNaoPropagaExcecaoENaoChamaSns() {
        when(repositorio.buscarNaoPublicados(50)).thenThrow(new RuntimeException("DB indisponivel"));

        assertThatCode(() -> job(50).publicarPendentes()).doesNotThrowAnyException();

        verifyNoInteractions(snsClient);
    }

    @Test
    void falhaNaSegundaLinhaInterrompeOLotePreservandoOrdemFifoDoPaciente() {
        EventoOutbox primeiro = evento(1L, 42L);
        EventoOutbox segundo = evento(2L, 42L);
        EventoOutbox terceiro = evento(3L, 42L);
        when(repositorio.buscarNaoPublicados(50)).thenReturn(List.of(primeiro, segundo, terceiro));
        when(repositorio.marcarComoPublicado(1L)).thenReturn(true);
        when(snsClient.publish(any(PublishRequest.class)))
                .thenReturn(PublishResponse.builder().build())
                .thenThrow(new RuntimeException("SNS indisponivel"));

        job(50).publicarPendentes();

        // A terceira linha (mesmo paciente da segunda, que falhou) nunca e
        // tentada nesta execucao -- publica-la fora de ordem violaria o
        // FIFO por MessageGroupId=pacienteId.
        verify(snsClient, times(2)).publish(any(PublishRequest.class));
        verify(repositorio).marcarComoPublicado(1L);
        verify(repositorio, never()).marcarComoPublicado(2L);
        verify(repositorio, never()).marcarComoPublicado(3L);
    }

    @Test
    void linhaJaMarcadaPorOutraInstanciaDoJobNaoInterrompeOLoteNemGeraErro() {
        EventoOutbox primeiro = evento(1L, 42L);
        EventoOutbox segundo = evento(2L, 43L);
        when(repositorio.buscarNaoPublicados(50)).thenReturn(List.of(primeiro, segundo));
        when(repositorio.marcarComoPublicado(1L)).thenReturn(false); // outra instancia venceu a corrida
        when(repositorio.marcarComoPublicado(2L)).thenReturn(true);

        assertThatCode(() -> job(50).publicarPendentes()).doesNotThrowAnyException();

        verify(snsClient, times(2)).publish(any(PublishRequest.class));
        verify(repositorio).marcarComoPublicado(1L);
        verify(repositorio).marcarComoPublicado(2L);
    }

    @Test
    void falhaAoMarcarComoPublicadoAposOAckDoSnsNaoPropagaExcecaoNemInterrompeOLote() {
        // I/O Matrix da spec 3.0: "Falha apos publicar, antes de marcar" --
        // o SNS ja confirmou, so o UPDATE que falhou; a linha continua
        // pendente (sera republicada), mas isso nao pode derrubar o job nem
        // impedir a proxima linha de ser processada nesta mesma execucao.
        EventoOutbox primeiro = evento(1L, 42L);
        EventoOutbox segundo = evento(2L, 43L);
        when(repositorio.buscarNaoPublicados(50)).thenReturn(List.of(primeiro, segundo));
        when(repositorio.marcarComoPublicado(1L)).thenThrow(new RuntimeException("DB indisponivel apos o ack"));
        when(repositorio.marcarComoPublicado(2L)).thenReturn(true);

        assertThatCode(() -> job(50).publicarPendentes()).doesNotThrowAnyException();

        verify(snsClient, times(2)).publish(any(PublishRequest.class));
        verify(repositorio).marcarComoPublicado(2L);
    }

    @Test
    void respeitaOTamanhoDeLoteConfigurado() {
        when(repositorio.buscarNaoPublicados(5)).thenReturn(List.of());

        job(5).publicarPendentes();

        verify(repositorio).buscarNaoPublicados(5);
    }

    @Test
    void falhaDeUmPacienteNaoBloqueiaAPublicacaoDeOutrosPacientesNoMesmoLote() {
        // Achado do code review (head-of-line blocking): so eventos
        // SEGUINTES do MESMO pacienteId da linha que falhou devem ser
        // pulados -- um paciente com falha permanente nao pode travar o
        // backlog inteiro de todos os outros pacientes no mesmo ciclo.
        EventoOutbox pacienteAPrimeiro = evento(1L, 42L);
        EventoOutbox pacienteASegundo = evento(2L, 42L);
        EventoOutbox pacienteB = evento(3L, 43L);
        when(repositorio.buscarNaoPublicados(50))
                .thenReturn(List.of(pacienteAPrimeiro, pacienteASegundo, pacienteB));
        when(repositorio.marcarComoPublicado(3L)).thenReturn(true);
        when(snsClient.publish(any(PublishRequest.class)))
                .thenThrow(new RuntimeException("SNS indisponivel para o paciente 42"))
                .thenReturn(PublishResponse.builder().build());

        job(50).publicarPendentes();

        // Paciente 42: primeiro evento falha, segundo e pulado (preserva FIFO).
        verify(repositorio, never()).marcarComoPublicado(1L);
        verify(repositorio, never()).marcarComoPublicado(2L);
        // Paciente 43: publicado normalmente, sem ser bloqueado pela falha do paciente 42.
        verify(repositorio).marcarComoPublicado(3L);
        verify(snsClient, times(2)).publish(any(PublishRequest.class));
    }

    @Test
    void messageGroupIdAusenteNoPayloadFalhaAPublicacaoSemChamarSnsNemMarcar() {
        // Achado do code review: String.valueOf(null) geraria a string
        // "null" como MessageGroupId em vez de recusar o evento malformado
        // -- deve falhar alto, tratado como falha de publicacao (mesmo
        // caminho de log/retry da falha de SNS, sem derrubar o job).
        EventoOutbox semPacienteId = new EventoOutbox(1L, UUID.randomUUID(), "ScoreCalculado",
                Instant.parse("2026-09-08T12:00:00Z"), 1, "corr-1", Map.of("triagemId", 7L));
        when(repositorio.buscarNaoPublicados(50)).thenReturn(List.of(semPacienteId));

        assertThatCode(() -> job(50).publicarPendentes()).doesNotThrowAnyException();

        verify(snsClient, never()).publish(any(PublishRequest.class));
        verify(repositorio, never()).marcarComoPublicado(anyLong());
    }

    @Test
    void falhaAoMontarOEnvelopeNaoChamaSnsNemMarcaEDistingueDeFalhaDePublicacao() {
        // Achado do code review: um bug de serializacao do envelope nao e
        // uma falha de rede do SNS -- os dois precisam de catches distintos
        // para nao confundir o diagnostico (e para nunca chegar a chamar o
        // SnsClient com um envelope quebrado).
        ObjectMapper objectMapperQuebrado = mock(ObjectMapper.class);
        when(objectMapperQuebrado.writeValueAsString(any()))
                .thenThrow(new RuntimeException("bug de serializacao"));
        RelaySnsPublisherJob jobComObjectMapperQuebrado =
                new RelaySnsPublisherJob(repositorio, snsClient, objectMapperQuebrado, TOPIC_ARN, 50);
        when(repositorio.buscarNaoPublicados(50)).thenReturn(List.of(evento(1L, 42L)));

        assertThatCode(jobComObjectMapperQuebrado::publicarPendentes).doesNotThrowAnyException();

        verify(snsClient, never()).publish(any(PublishRequest.class));
        verify(repositorio, never()).marcarComoPublicado(anyLong());
    }

    @Test
    void topicArnVazioComRelayHabilitadoFalhaNaConstrucaoDoJob() {
        // Achado do code review (fail-fast): sem isso, o relay habilitado
        // com topic-arn vazio rodaria para sempre falhando em silencio a
        // cada ciclo, em vez de derrubar a subida do servico.
        assertThatThrownBy(() -> new RelaySnsPublisherJob(repositorio, snsClient, objectMapper, "", 50))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new RelaySnsPublisherJob(repositorio, snsClient, objectMapper, "   ", 50))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new RelaySnsPublisherJob(repositorio, snsClient, objectMapper, null, 50))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void batchSizeMenorOuIgualAZeroEClampeadoParaUm() {
        // Achado do code review: batch-size <= 0 quebraria a query de
        // leitura (LIMIT invalido) a cada execucao -- pisado em 1 em vez de
        // propagar o valor invalido.
        when(repositorio.buscarNaoPublicados(1)).thenReturn(List.of());

        new RelaySnsPublisherJob(repositorio, snsClient, objectMapper, TOPIC_ARN, 0).publicarPendentes();

        verify(repositorio).buscarNaoPublicados(1);
    }
}
