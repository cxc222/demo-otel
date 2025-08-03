package com.demo.otel;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnClass(name = {
        "io.opentelemetry.api.OpenTelemetry",
        "org.springframework.web.servlet.DispatcherServlet"
})
@ConditionalOnProperty(name = "opentelemetry.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties
@Import({
        OpenTelemetryConfig.class,
        SpanFilterConfig.class,
        OpenTelemetryWebFilter.class,
        RqueueInstrumentation.class
})
public class CustomOpenTelemetryAutoConfiguration {

    // 这个类主要用于Spring Boot自动配置
    // 可以添加更多的条件化Bean配置
}
