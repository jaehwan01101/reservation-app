package com.eksproject.reservation.ops;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * "버그 있는 새 버전"을 흉내내는 장애 주입 필터.
 * Rollout 의 새 revision 에만 APP_FAULT_ERROR_RATE=0.3 같은 env 를 넣으면
 * canary 단계에서 에러율이 튀고 → AnalysisTemplate 가 실패 → 자동 롤백되는 흐름을 시연할 수 있다.
 * 기본 필터 순서(최하위)라서 Micrometer 의 http.server.requests 에 500 으로 정상 집계된다.
 */
@Component
public class FaultInjectionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FaultInjectionFilter.class);

    private final FaultProperties props;

    public FaultInjectionFilter(FaultProperties props) {
        this.props = props;
        if (props.enabled()) {
            log.warn("장애 주입 활성화됨: errorRate={}, latencyMs={}", props.errorRate(), props.latencyMs());
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !props.enabled() || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (props.latencyMs() > 0) {
            try {
                Thread.sleep(props.latencyMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (ThreadLocalRandom.current().nextDouble() < props.errorRate()) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("""
                    {"type":"about:blank","title":"Internal Server Error","status":500,"detail":"injected fault"}""");
            return;
        }
        chain.doFilter(request, response);
    }
}
