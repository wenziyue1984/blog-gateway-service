package com.wenziyue.gateway.filter;

import com.wenziyue.auth.core.constants.AuthConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * @author wenziyue
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(AuthConstants.TRACE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        // 1) 写入下游请求头
        ServerHttpRequest mutatedRequest = exchange.getRequest()
                .mutate()
                .header(AuthConstants.TRACE_ID_HEADER, traceId)
                .build();

        // 2) 可选：回写给客户端，方便前端/排障
        exchange.getResponse().getHeaders().set(AuthConstants.TRACE_ID_HEADER, traceId);

        // 3) 网关自己日志也用它（注意：这是轻量做法）
        MDC.put("traceId", traceId);

        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

        return chain.filter(mutatedExchange)
                .doFinally(signalType -> MDC.remove("traceId"));
    }

    @Override
    public int getOrder() {
        // 放在 JwtAuthGlobalFilter 之前，所有日志都带有 traceId
        return -200;
    }
}
