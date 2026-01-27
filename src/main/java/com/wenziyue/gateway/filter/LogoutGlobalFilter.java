package com.wenziyue.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wenziyue.auth.core.constants.AuthConstants;
import com.wenziyue.auth.core.jwt.JwtUtils;
import com.wenziyue.auth.core.model.TokenExpireDTO;
import com.wenziyue.redis.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogoutGlobalFilter implements GlobalFilter, Ordered {

    private final JwtUtils jwtUtils;
    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;

    @Value("${wenziyue.logout-path}")
    private String LOGOUT_PATH;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        if (!LOGOUT_PATH.equals(path) || method != HttpMethod.POST) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(AuthConstants.AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(AuthConstants.TOKEN_PREFIX)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(AuthConstants.TOKEN_PREFIX.length()).trim();

        String userId;
        try {
            userId = jwtUtils.getUserIdFromToken(token);
        } catch (Exception e) {
            log.warn("logout 解析 token 失败: {}", e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        if (!StringUtils.hasText(userId)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // 因为 RedisUtils 基于 RedisTemplate 是阻塞式的，
        // 在 Gateway 里先用 boundedElastic 做隔离，避免堵 Reactor 线程
        return Mono.fromRunnable(() -> {
                    // 删除对应token，并且维护活跃token集合
                    String tokenKey = AuthConstants.LOGIN_USER_TOKEN_KEY_PREFIX + token;
                    String tokenSetKey = AuthConstants.LOGIN_USER_TOKENS_SET_KEY_PREFIX + userId;
                    redisUtils.delete(tokenKey);
                    val tokenSet = redisUtils.sMembers(tokenSetKey);
                    if (tokenSet != null) {
                        tokenSet.forEach(dto -> {
                            val tokenExpireDTO = objectMapper.convertValue(dto, TokenExpireDTO.class);
                            if (System.currentTimeMillis() > tokenExpireDTO.getExpireTimeStamp() || tokenExpireDTO.getToken().equals(token)) {
                                redisUtils.sRemove(tokenSetKey, dto);
                                // 删除失效token，其实不是必须的，因为redis会自动删除过期的key，但是为了安全起见，还是删除了
                                redisUtils.delete(AuthConstants.LOGIN_USER_TOKEN_KEY_PREFIX + tokenExpireDTO.getToken());
                            }
                        });
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then(writeOk(exchange));
    }

    private Mono<Void> writeOk(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = "{\"code\":0,\"msg\":\"logout ok\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(bytes))
        );
    }

    @Override
    public int getOrder() {
        // 放在你的 JwtAuthGlobalFilter 后面或同级偏后都行
        return -90;
    }
}