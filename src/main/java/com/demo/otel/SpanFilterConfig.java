package com.demo.otel;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Set;

@Configuration
public class SpanFilterConfig {
    @Bean
    @ConfigurationProperties(prefix = "opentelemetry.filter")
    public FilterProperties filterProperties() {
        return new FilterProperties();
    }

    @Bean
    public Sampler customSampler(FilterProperties filterProperties) {
        return new FilteringSampler(filterProperties);
    }

    public static class FilterProperties {
        private Set<String> excludeUrls = Set.of(
                "/actuator/health/**",
                "/actuator/metrics/**",
                "/actuator/prometheus/**"
        );

        private Set<String> excludeOperations = Set.of(
                "PING",
                "SELECT 1",
                "redis.ping",
                "mysql.ping"
        );

        private Set<String> excludeSpanNames = Set.of(
                "jedis.ping",
                "lettuce.ping",
                "actuator.health"
        );

        // getters and setters
        public Set<String> getExcludeUrls() { return excludeUrls; }
        public void setExcludeUrls(Set<String> excludeUrls) { this.excludeUrls = excludeUrls; }

        public Set<String> getExcludeOperations() { return excludeOperations; }
        public void setExcludeOperations(Set<String> excludeOperations) { this.excludeOperations = excludeOperations; }

        public Set<String> getExcludeSpanNames() { return excludeSpanNames; }
        public void setExcludeSpanNames(Set<String> excludeSpanNames) { this.excludeSpanNames = excludeSpanNames; }
    }

    public static class FilteringSampler implements Sampler {
        private final FilterProperties filterProperties;
        private final AntPathMatcher pathMatcher = new AntPathMatcher();
        private final Sampler delegate = Sampler.traceIdRatioBased(1.0);

        public FilteringSampler(FilterProperties filterProperties) {
            this.filterProperties = filterProperties;
        }

        @Override
        public SamplingResult shouldSample(
                Context parentContext,
                String traceId,
                String name,
                SpanKind spanKind,
                Attributes attributes,
                List<LinkData> parentLinks) {

            // 过滤健康检查相关的URL
            String httpTarget = attributes.get(SemanticAttributes.HTTP_TARGET);
            if (httpTarget != null) {
                for (String excludeUrl : filterProperties.getExcludeUrls()) {
                    if (pathMatcher.match(excludeUrl, httpTarget)) {
                        return SamplingResult.create(SamplingDecision.NOT_RECORD);
                    }
                }
            }

            // 过滤数据库PING操作
            String dbStatement = attributes.get(SemanticAttributes.DB_STATEMENT);
            if (dbStatement != null) {
                for (String excludeOp : filterProperties.getExcludeOperations()) {
                    if (dbStatement.toUpperCase().contains(excludeOp.toUpperCase())) {
                        return SamplingResult.create(SamplingDecision.NOT_RECORD);
                    }
                }
            }

            // 过滤Redis PING操作
            String redisCommand = attributes.get(SemanticAttributes.DB_OPERATION);
            if ("PING".equalsIgnoreCase(redisCommand)) {
                return SamplingResult.create(SamplingDecision.NOT_RECORD);
            }

            // 过滤指定的span名称
            for (String excludeSpanName : filterProperties.getExcludeSpanNames()) {
                if (name.toLowerCase().contains(excludeSpanName.toLowerCase())) {
                    return SamplingResult.create(SamplingDecision.NOT_RECORD);
                }
            }

            // 其他情况使用默认采样器
            return delegate.shouldSample(parentContext, traceId, name, spanKind, attributes, parentLinks);
        }

        @Override
        public String getDescription() {
            return "FilteringSampler";
        }
    }
}
