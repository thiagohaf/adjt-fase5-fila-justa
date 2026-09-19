package com.filajusta.gateway.infrastructure.web;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * {@link GlobalFilter} ({@link Ordered}) -- gera/propaga o header
 * {@code X-Correlation-Id} para toda requisicao que passa pelo gateway
 * (spec correlationId-gateway). Le o header da requisicao recebida; se
 * ausente, em branco ou invalido (fora do padrao ASCII imprimivel sem
 * caracteres de controle, ate {@value #MAX_LENGTH} caracteres -- mesmo
 * fallback do caso ausente, evita repassar um valor malformado que faria o
 * framework HTTP rejeitar o header na requisicao/resposta com um 500), gera
 * um UUID novo. Se o cliente enviar multiplos valores do mesmo header, usa
 * apenas o primeiro ({@code HttpHeaders#getFirst}) -- um unico valor
 * deterministico por requisicao (Boundaries da spec). O mesmo valor e
 * propagado tanto na requisicao encaminhada ao servico downstream (via
 * {@code RouteLocator}) quanto em toda resposta ao cliente -- inclusive os
 * {@code 401} do {@link com.filajusta.gateway.infrastructure.security.JwtAuthenticationFilter},
 * o que exige {@link #getOrder()} com precedencia maior (menor valor) que a
 * daquele filtro.
 *
 * <p>Nao ha propagacao para {@code auth-service} ou demais servicos via
 * chamada de rede propria aqui -- o header ja viaja naturalmente na
 * requisicao encaminhada pelo {@code RouteLocator} (Boundaries da spec).
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    // Tamanho razoavel para um identificador de correlacao (bem acima de um
    // UUID de 36 caracteres, mas sem abrir espaco para payloads grandes
    // injetados via header por um cliente).
    private static final int MAX_LENGTH = 128;

    // ASCII imprimivel (0x20-0x7E), sem caracteres de controle -- exclui CR
    // ("\r", 0x0D) e LF ("\n", 0x0A), que o framework HTTP rejeitaria ao
    // setar o header na requisicao/resposta (resultaria em 500 em vez de
    // cair no fallback de UUID novo).
    private static final Pattern VALOR_VALIDO = Pattern.compile("^[\\x20-\\x7E]{1," + MAX_LENGTH + "}$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String recebido = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        String correlationId = valido(recebido) ? recebido : UUID.randomUUID().toString();

        ServerHttpRequest requestMutado = exchange.getRequest().mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .build();

        // Setado antes de chain.filter: precisa estar presente mesmo quando
        // um filtro downstream (ex.: JwtAuthenticationFilter) responde
        // direto no exchange (401) sem prosseguir a cadeia ate um handler.
        exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);

        return chain.filter(exchange.mutate().request(requestMutado).build());
    }

    @Override
    public int getOrder() {
        // Valor minimo possivel -- primeiro filtro do pipeline, por
        // definicao (Design Notes da spec): nenhum outro cross-cutting
        // concern do gateway precisa rodar antes da geracao do
        // correlationId, e toda resposta (inclusive 401 do
        // JwtAuthenticationFilter, getOrder() = HIGHEST_PRECEDENCE + 100)
        // precisa carregar o header.
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static boolean valido(String valor) {
        return valor != null && VALOR_VALIDO.matcher(valor).matches();
    }
}
