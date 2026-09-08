package com.filajusta.triagem.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova, contra um Postgres 18 real via Testcontainers (sem LocalStack --
 * este teste nao envolve SNS), a garantia exigida pela I/O &amp; Edge-Case
 * Matrix da spec 3.0 (frozen): "Multiplas instancias do job rodando -&gt;
 * Nenhuma linha e publicada duas vezes por corrida (lock otimista ou
 * {@code SELECT ... FOR UPDATE SKIP LOCKED})".
 *
 * <p>Achado do code review: o teste
 * {@code RelaySnsPublisherJobTest.linhaJaMarcadaPorOutraInstanciaDoJobNaoInterrompeOLoteNemGeraErro}
 * so mockava {@code marcarComoPublicado} retornando {@code false} -- provava
 * a protecao da MARCACAO, nunca da LEITURA. Um {@code SELECT} simples (sem
 * lock) deixaria duas instancias do job lerem e publicarem a MESMA linha
 * pendente antes que qualquer uma a marcasse. Este teste abre duas
 * transacoes concorrentes de verdade (via {@link TransactionTemplate},
 * {@code PROPAGATION_REQUIRES_NEW}, cada uma numa conexao/thread propria) e
 * confirma que {@code EventoOutboxJpaRepository#buscarPendentesParaAtualizar}
 * ({@code FOR UPDATE SKIP LOCKED}) nunca devolve a mesma linha pendente para
 * as duas enquanto a primeira ainda nao commitou -- exatamente o contrato
 * transacional documentado em {@code EventoOutboxRepositorio} (a leitura so
 * protege de fato quando chamada dentro da mesma transacao que tambem
 * publica e marca, como faz {@code RelaySnsPublisherJob.publicarPendentes}).
 */
@Testcontainers
@SpringBootTest
@TestPropertySource(properties = "filajusta.triagem.relay.enabled=false")
class EventoOutboxJpaRepositoryConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private EventoOutboxJpaRepository jpaRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void duasLeiturasConcorrentesNuncaRetornamAMesmaLinhaPendenteParaAsDuas() throws Exception {
        for (int i = 0; i < 4; i++) {
            jpaRepository.save(new EventoOutboxJpaEntity(
                    UUID.randomUUID(), "ScoreCalculado", Instant.now(), 1, "corr-" + i,
                    "{\"pacienteId\":" + i + "}"));
        }

        CountDownLatch primeiraTransacaoLeuEBloqueou = new CountDownLatch(1);
        CountDownLatch podeLiberarAPrimeiraTransacao = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // Primeira transacao: le (e bloqueia via SKIP LOCKED) 2 linhas,
            // avisa que ja leu, depois FICA PARADA segurando a transacao
            // aberta (lock ainda de pe) ate ser liberada explicitamente.
            Callable<List<Long>> primeiraTransacao = () -> novoTemplate().execute(status -> {
                List<Long> ids = idsDosPendentes(2);
                primeiraTransacaoLeuEBloqueou.countDown();
                aguardar(podeLiberarAPrimeiraTransacao);
                return ids;
            });
            Future<List<Long>> resultado1 = executor.submit(primeiraTransacao);
            assertThat(primeiraTransacaoLeuEBloqueou.await(10, TimeUnit.SECONDS))
                    .as("primeira transacao deveria ter lido e sinalizado dentro do timeout")
                    .isTrue();

            // Segunda transacao roda CONCORRENTEMENTE, enquanto a primeira
            // ainda segura o lock (nao commitou) -- com SKIP LOCKED, deve
            // pular as linhas ja bloqueadas e pegar outras, nunca as mesmas.
            Callable<List<Long>> segundaTransacao = () -> novoTemplate().execute(status -> idsDosPendentes(2));
            Future<List<Long>> resultado2 = executor.submit(segundaTransacao);
            List<Long> ids2 = resultado2.get(10, TimeUnit.SECONDS);

            podeLiberarAPrimeiraTransacao.countDown();
            List<Long> ids1 = resultado1.get(10, TimeUnit.SECONDS);

            assertThat(ids1).hasSize(2);
            assertThat(ids2).hasSize(2);
            assertThat(Collections.disjoint(ids1, ids2))
                    .as("duas leituras concorrentes (SELECT ... FOR UPDATE SKIP LOCKED) nunca devem "
                            + "retornar a mesma linha pendente para as duas -- ids1=%s ids2=%s", ids1, ids2)
                    .isTrue();
        } finally {
            executor.shutdown();
        }
    }

    private List<Long> idsDosPendentes(int limite) {
        return jpaRepository.buscarPendentesParaAtualizar(limite).stream()
                .map(EventoOutboxJpaEntity::getId)
                .toList();
    }

    private TransactionTemplate novoTemplate() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private static void aguardar(CountDownLatch latch) {
        try {
            boolean liberado = latch.await(10, TimeUnit.SECONDS);
            if (!liberado) {
                throw new IllegalStateException("timeout esperando liberacao da primeira transacao");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrompido esperando liberacao da primeira transacao", e);
        }
    }
}
