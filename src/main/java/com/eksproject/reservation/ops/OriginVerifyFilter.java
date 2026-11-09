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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * CloudFront 를 거치지 않은 /api/** 요청을 403 으로 거절한다.
 * ALB 를 직접 호출하면 CloudFront 의 캐시·보안 헤더 정책을 우회하게 되므로,
 * CloudFront 가 origin 요청마다 붙이는 시크릿 헤더가 있는 요청만 통과시킨다.
 * 헬스체크(/livez, /readyz)와 관리 포트(8081)는 /api/ 밖이라 대상이 아니다.
 * 기본 필터 순서(최하위)라서 거절된 요청도 http.server.requests 에 403 으로 집계된다.
 */
@Component
public class OriginVerifyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OriginVerifyFilter.class);

    private final OriginVerifyProperties props;
    private final byte[] expected;

    public OriginVerifyFilter(OriginVerifyProperties props) {
        this.props = props;
        this.expected = props.enabled() ? props.secret().getBytes(StandardCharsets.UTF_8) : new byte[0];
        if (!props.enabled()) {
            log.warn("origin 헤더 검증 비활성화됨 (로컬/테스트 용도. 운영에서는 ORIGIN_VERIFY_ENABLED=true)");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !props.enabled() || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String actual = request.getHeader(props.headerName());
        // 길이가 같을 때 비교 시간이 내용과 무관하도록 MessageDigest.isEqual 사용 (타이밍 공격 방지)
        if (actual != null && MessageDigest.isEqual(expected, actual.getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
            return;
        }
        // 받은 헤더 값은 로그에 남기지 않는다.
        log.debug("origin 헤더 검증 실패: {} {}", request.getMethod(), request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"type":"about:blank","title":"Forbidden","status":403}""");
    }
}
