package com.eksproject.reservation.ops;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * CloudFront 가 붙여주는 시크릿 헤더 검증 설정.
 *
 * @param enabled    false 면 검증하지 않는다 (로컬 개발, 테스트). 운영(K8s)에서는 true 로 켠다.
 * @param headerName CloudFront origin custom header 이름 (Terraform 60-edge 의 custom_header 와 같아야 함)
 * @param secret     헤더 값. External Secrets 가 만든 K8s Secret 에서 env 로 주입된다.
 */
@ConfigurationProperties("app.origin-verify")
public record OriginVerifyProperties(
        boolean enabled,
        @DefaultValue("X-Origin-Verify") String headerName,
        String secret) {

    public OriginVerifyProperties {
        // 켜져 있는데 시크릿이 비어 있으면 "아무 요청이나 통과"가 아니라 "기동 실패"로 만든다.
        // (ExternalSecret 동기화 실패 → Secret 없음 → Pod 가 Ready 되지 않아 Rollout 이 진행되지 않음)
        if (enabled && (secret == null || secret.isBlank())) {
            throw new IllegalStateException("app.origin-verify.enabled=true 인데 secret 이 비어 있습니다");
        }
    }
}
