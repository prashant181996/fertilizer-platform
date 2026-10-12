package com.fertiplus.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {
    @Bean
    public KeyResolver clientKeyResolver() {
        return exchange -> Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst("X-API-Key")).filter(s -> !s.isBlank()).switchIfEmpty(Mono.just(clientIp(exchange)));
    }

    private String clientIp(ServerWebExchange e) {
        var a = e.getRequest().getRemoteAddress();
        return a == null ? "unknown" : a.getAddress().getHostAddress();
    }
}
