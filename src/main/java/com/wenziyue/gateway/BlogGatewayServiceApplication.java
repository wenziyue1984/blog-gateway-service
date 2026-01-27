package com.wenziyue.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author wenziyue
 */

@SpringBootApplication(scanBasePackages = {"com.wenziyue"})
public class BlogGatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlogGatewayServiceApplication.class, args);
    }
}
