package com.wenziyue.gateway.config;

import com.wenziyue.auth.core.jwt.JwtUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author wenziyue
 */
@Configuration
public class JwtBeanConfiguration {

    @Bean
    public JwtUtils jwtUtils(BlogGatewayProperties properties) {
        return new JwtUtils(properties);
    }

}
