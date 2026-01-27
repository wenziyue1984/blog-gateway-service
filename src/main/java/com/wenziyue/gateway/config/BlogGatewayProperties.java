package com.wenziyue.gateway.config;

import com.wenziyue.auth.core.jwt.JwtConfig;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 网关专用的 JWT 配置，继承 auth-core 里的 JwtConfig
 *
 * @author wenziyue
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "wenziyue.auth.jwt")
@RefreshScope
public class BlogGatewayProperties extends JwtConfig{

    /**
     * 白名单路径（不需要登录认证的路径），支持用户自定义
     */
    private List<String> whiteList = new ArrayList<>();


    @PostConstruct
    public void init() {
        // 默认token过期时间，7天
        if (getExpireSeconds() == null) {
            setExpireSeconds(7 * 24 * 60 * 60L);
        }
        // 默认刷新token时间，1天
        if (getRefreshBeforeExpirationSeconds() == null) {
            setRefreshBeforeExpirationSeconds(24 * 60 * 60L);
        }
        // 校验参数
        validate();
        // 默认白名单路径
        List<String> defaultWhiteList = Arrays.asList(
                "/**/doc.html",
                "/**/swagger-ui/**",
                "/**/swagger-resources/**",
                "/**/v3/api-docs/**",
                "/**/swagger-ui.html",
                "/**/webjars/swagger-ui/**"
        );
        // 合并默认路径（避免重复添加）
        for (String path : defaultWhiteList) {
            if (!whiteList.contains(path)) {
                whiteList.add(path);
            }
        }
    }

    /**
     * 校验参数
     */
    private void validate() {
        if (!StringUtils.hasText(getJwtSecret())) {
            throw new IllegalArgumentException("jwtSecret cannot be empty");
        }
        if (getJwtSecret().length() < 16) {
            throw new IllegalArgumentException("jwtSecret must be at least 16 characters long");
        }

        if (getExpireSeconds() <= 0) {
            throw new IllegalArgumentException("expire must be greater than 0");
        }
        if (getRefreshBeforeExpirationSeconds() <= 0) {
            throw new IllegalArgumentException("refreshBeforeExpiration must be greater than 0");
        }
        if (getExpireSeconds() <= getRefreshBeforeExpirationSeconds()) {
            throw new IllegalArgumentException("expire must be greater than refreshBeforeExpiration");
        }
    }
}
