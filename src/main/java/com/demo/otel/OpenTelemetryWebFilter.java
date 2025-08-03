package com.demo.otel;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@Order(1)
public class OpenTelemetryWebFilter extends OncePerRequestFilter {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // 不需要追踪的端点
    private final Set<String> excludePaths = Set.of(
            "/actuator/health/**",
            "/actuator/metrics/**",
            "/actuator/prometheus/**",
            "/actuator/info/**",
            "/favicon.ico"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestUri = request.getRequestURI();

        // 检查是否需要跳过追踪
        boolean shouldSkip = excludePaths.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, requestUri));

        if (shouldSkip) {
            // 如果当前存在span，标记为不记录
            Span currentSpan = Span.current();
            if (currentSpan != null && !currentSpan.getSpanContext().isValid()) {
                // 这里可以选择结束span或者设置为不采样
                currentSpan.setStatus(StatusCode.OK);
                currentSpan.setAttribute("otel.sampling.rule", "excluded_endpoint");
            }
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return excludePaths.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, requestUri));
    }
}
