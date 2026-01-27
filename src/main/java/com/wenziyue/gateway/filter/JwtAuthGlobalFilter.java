package com.wenziyue.gateway.filter;

import com.wenziyue.auth.core.constants.AuthConstants;
import com.wenziyue.auth.core.enums.UserStatusEnum;
import com.wenziyue.auth.core.header.HeaderUtils;
import com.wenziyue.auth.core.jwt.JwtUtils;
import com.wenziyue.auth.core.model.LoginUser;
import com.wenziyue.auth.core.model.TokenExpireDTO;
import com.wenziyue.gateway.config.BlogGatewayProperties;
import com.wenziyue.redis.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;


/**
 * 网关全局鉴权过滤器（v1：只解析 userId）
 *
 * @author wenziyue
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered{

    private final JwtUtils jwtUtils;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final BlogGatewayProperties blogGatewayProperties;
    private final RedisUtils redisUtils;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // 白名单路径，直接放行
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        HttpHeaders headers = exchange.getRequest().getHeaders();
        String authHeader = headers.getFirst(AuthConstants.AUTHORIZATION_HEADER);

        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(AuthConstants.TOKEN_PREFIX)) {
            log.debug("未携带合法 Authorization 头，path={}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(AuthConstants.TOKEN_PREFIX.length()).trim();

        String userId;
        try {
            userId = jwtUtils.getUserIdFromToken(token);
        } catch (Exception e) {
            log.error("解析 JWT 失败，path={}, msg={}", path, e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        if (!StringUtils.hasText(userId)) {
            log.error("token 中未解析出 userId，path={}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // 从redis中token是否生效
        val tokenIsLive = redisUtils.get(AuthConstants.LOGIN_USER_TOKEN_KEY_PREFIX + token);
        if (tokenIsLive == null) {
            log.error("redis中用户token不存在，userId={}, token={}", userId, token);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        // 从redis中获取用户信息
        val loginUser = redisUtils.get(AuthConstants.LOGIN_USER_INFO_KEY_PREFIX + userId, LoginUser.class);
        if (loginUser == null) {
            // 缓存失效直接拒绝访问
            log.warn("redis中用户信息不存在，userId={}", userId);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        } else if (UserStatusEnum.DISABLED.equals(loginUser.getStatus())) {
            // 检查用户状态，禁用则拒绝访问
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // 将用户信息填充进请求头
        var mutatedRequest = exchange.getRequest()
                .mutate()
                .header(AuthConstants.USER_ID_HEADER, userId)
                .header(AuthConstants.USER_INFO_HEADER, HeaderUtils.serializeUserInfoToHeader(loginUser))
                .build();

        var mutatedExchange = exchange.mutate().request(mutatedRequest).build();

        // ==== 添加续期逻辑 ====
        try {
            long remain = jwtUtils.getExpirationRemaining(token);
            // 如果 oldToken 剩余时间不足，生成新 oldToken 返回给前端
            if (remain < blogGatewayProperties.getRefreshBeforeExpirationSeconds()) {
                String newToken = jwtUtils.generateToken(userId);
                exchange.getResponse().getHeaders().set(AuthConstants.REFRESH_TOKEN_HEADER, AuthConstants.TOKEN_PREFIX + newToken);
                // 这里本来打算删除redis中的旧token，然后将活跃token集合中的旧token删除再添加新token，但是这样有个问题，如果新token并没有更新成功，然后用户下个请求仍然带着旧token就会访问失败重新登录了，这样体验并不好，所以这里只添加新token，并不删除旧token
                redisUtils.set(AuthConstants.LOGIN_USER_TOKEN_KEY_PREFIX + newToken, 1, blogGatewayProperties.expireSeconds, TimeUnit.SECONDS);
                // 刷新token的时候也更新缓存（这里只续期即可，如果用户信息变化的话用户服务会更新缓存。也就是此缓存的内容完全由用户服务维护）
                redisUtils.expire(AuthConstants.LOGIN_USER_INFO_KEY_PREFIX + userId, blogGatewayProperties.expireSeconds, TimeUnit.SECONDS);
                // 更新用户活跃token集合
                val tokenExpireDTO = TokenExpireDTO.builder().token(newToken).expireTimeStamp(System.currentTimeMillis() + (blogGatewayProperties.getExpireSeconds() * 1000)).build();
                redisUtils.sAddAndExpire(AuthConstants.LOGIN_USER_TOKENS_SET_KEY_PREFIX + userId, blogGatewayProperties.expireSeconds, TimeUnit.SECONDS, tokenExpireDTO);
            }
        } catch (Exception ignore) {
            // 续期失败不影响本次鉴权通过
        }

        return chain.filter(mutatedExchange);
    }

    private boolean isWhitelisted(String path) {
        for (String pattern : blogGatewayProperties.getWhiteList()) {
            if (pathMatcher.match(pattern, path)) {
//                log.info("logout 匹配白名单: {}", path);
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return -100; // 尽量靠前
    }
}
