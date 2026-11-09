package com.eksproject.reservation.ops;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param errorRate 0.0 ~ 1.0, /api/** 요청 중 이 비율만큼 500 응답
 * @param latencyMs /api/** 요청마다 추가할 지연(ms)
 */
@ConfigurationProperties("app.fault")
public record FaultProperties(double errorRate, long latencyMs) {

    public boolean enabled() {
        return errorRate > 0 || latencyMs > 0;
    }
}
