package com.wenziyue.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * @author wenziyue
 */
@Slf4j
@Component
public class AccessLogGlobalFilter implements GlobalFilter, Ordered {

    private static final String START_TIME_ATTR = "accessLogStartTime";

    private static final Logger ACCESS = LoggerFactory.getLogger("ACCESS_LOG");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        exchange.getAttributes().put(START_TIME_ATTR, System.currentTimeMillis());

        return chain.filter(exchange).doFinally(signalType -> {
            Long start = exchange.getAttribute(START_TIME_ATTR);
            long costMs = start == null ? -1 : (System.currentTimeMillis() - start);

            ServerHttpRequest req = exchange.getRequest();
            ServerHttpResponse resp = exchange.getResponse();

            String method = req.getMethodValue();
            String path = req.getURI().getRawPath();
            String query = Optional.ofNullable(req.getURI().getRawQuery()).map(q -> "?" + q).orElse("");
            String status = resp.getStatusCode() == null ? "-" : String.valueOf(resp.getStatusCode().value());

            InetSocketAddress remote = req.getRemoteAddress();
            String clientIp = remote == null ? "-" : remote.getAddress().getHostAddress();

            // Spring Cloud Gateway 会把最终路由目标放到这个 attribute 里
            Object routeUri = exchange.getAttributes().getOrDefault("org.springframework.cloud.gateway.support.ServerWebExchangeUtils.gatewayRequestUrl", "-");

//            // 你如果有 TraceId（MDC 或 header），可以在这里拿出来一起打印
            String traceId = req.getHeaders().getFirst("traceId");
            if (traceId == null || traceId.isEmpty()) traceId = req.getHeaders().getFirst("X-Trace-Id");
            if (traceId == null || traceId.isEmpty()) traceId = "-";

            // 一行 access log：clientIp method path status cost route traceId
            ACCESS.info("access ip={} method={} uri={}{} status={} costMs={} route={} traceId={}",
                    clientIp, method, path, query, status, costMs, routeUri, traceId);
        });
    }

    @Override
    public int getOrder() {
        // 越小越先执行；这里用一个偏前的位置
        return -150;
    }
}
