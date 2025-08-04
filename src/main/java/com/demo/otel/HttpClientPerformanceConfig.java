package com.demo.otel;

import io.opentelemetry.api.OpenTelemetry;
import lombok.Data;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class HttpClientPerformanceConfig {

    @Bean
    @ConfigurationProperties(prefix = "http.client")
    public HttpClientProperties httpClientProperties() {
        return new HttpClientProperties();
    }

    /**
     * 高性能OkHttp客户端配置 - 使用原生API
     * OpenTelemetry Java Agent会自动添加追踪功能
     */
    @Bean("highPerformanceOkHttpClient")
    public OkHttpClient highPerformanceOkHttpClient(HttpClientProperties properties) {
        return new OkHttpClient.Builder()
                // 连接池配置 - 重用连接提高性能
                .connectionPool(new ConnectionPool(
                        properties.getMaxIdleConnections(),    // 最大空闲连接数
                        properties.getKeepAliveDuration(),     // 连接保持时间
                        TimeUnit.MINUTES
                ))
                // 超时配置
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeout()))
                .readTimeout(Duration.ofMillis(properties.getReadTimeout()))
                .writeTimeout(Duration.ofMillis(properties.getWriteTimeout()))
                .callTimeout(Duration.ofMillis(properties.getCallTimeout()))
                // 重试配置
                .retryOnConnectionFailure(properties.isRetryOnConnectionFailure())
                // 重定向配置
                .followRedirects(properties.isFollowRedirects())
                .followSslRedirects(properties.isFollowSslRedirects())
                // 请求/响应大小限制
                .addInterceptor(chain -> {
                    okhttp3.Request request = chain.request();
                    okhttp3.Response response = chain.proceed(request);

                    // 可以在这里添加请求/响应大小检查
                    if (response.body() != null) {
                        long contentLength = response.body().contentLength();
                        if (contentLength > properties.getMaxResponseSize()) {
                            throw new RuntimeException("Response too large: " + contentLength);
                        }
                    }
                    return response;
                })
                .build();
    }

    /**
     * 高性能RestTemplate配置
     */
    @Bean
    public RestTemplate highPerformanceRestTemplate(OkHttpClient okHttpClient,
                                                    HttpClientProperties properties) {
        OkHttp3ClientHttpRequestFactory factory = new OkHttp3ClientHttpRequestFactory(okHttpClient);
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        factory.setWriteTimeout(properties.getWriteTimeout());

        RestTemplate restTemplate = new RestTemplate(factory);

        // 添加错误处理器
        restTemplate.setErrorHandler(new CustomResponseErrorHandler());

        return restTemplate;
    }

    /**
     * 高性能WebClient配置
     */
    @Bean
    public WebClient highPerformanceWebClient(HttpClientProperties properties) {
        return WebClient.builder()
                .clientConnector(
                        new ReactorClientHttpConnector(
                                HttpClient.create()
                                        .responseTimeout(Duration.ofMillis(properties.getReadTimeout()))
                                        .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS,
                                                properties.getConnectTimeout())
                                        .option(io.netty.channel.ChannelOption.SO_KEEPALIVE, true)
                                        .option(io.netty.channel.ChannelOption.TCP_NODELAY, true)
                        )
                )
                .codecs(configurer -> {
                    configurer.defaultCodecs().maxInMemorySize(properties.getMaxResponseSize());
                })
                .build();
    }

    /**
     * HTTP客户端属性配置类
     */
    @Data
    public static class HttpClientProperties {
        // 连接池配置
        private int maxIdleConnections = 20;
        private int keepAliveDuration = 5; // 分钟

        // 超时配置
        private int connectTimeout = 10000;  // 10秒
        private int readTimeout = 30000;     // 30秒
        private int writeTimeout = 30000;    // 30秒
        private int callTimeout = 60000;     // 60秒

        // 重试和重定向
        private boolean retryOnConnectionFailure = true;
        private boolean followRedirects = true;
        private boolean followSslRedirects = true;

        // 大小限制
        private int maxResponseSize = 10 * 1024 * 1024; // 10MB

        // 追踪配置
        private List<String> capturedRequestHeaders = List.of(
                "Authorization", "Content-Type", "Accept", "User-Agent"
        );
        private List<String> capturedResponseHeaders = List.of(
                "Content-Type", "Content-Length", "Cache-Control"
        );
    }

    /**
     * 自定义错误处理器
     * 可以添加span属性，但span本身由Java Agent管理
     */
    public static class CustomResponseErrorHandler implements org.springframework.web.client.ResponseErrorHandler {

        @Override
        public boolean hasError(org.springframework.http.client.ClientHttpResponse response)
                throws java.io.IOException {
            return !response.getStatusCode().is2xxSuccessful();
        }

        @Override
        public void handleError(org.springframework.http.client.ClientHttpResponse response)
                throws java.io.IOException {

            // 记录错误信息到当前span（如果存在）
            try {
                io.opentelemetry.api.trace.Span currentSpan = io.opentelemetry.api.trace.Span.current();
                if (currentSpan != null) {
                    currentSpan.setAttribute("http.error.status_code", response.getStatusCode().value());
                    currentSpan.setAttribute("http.error.reason", response.getStatusText());

                    // 根据状态码进行不同处理
                    HttpStatusCode statusCode = response.getStatusCode();
                    currentSpan.setAttribute("error.type",
                            statusCode.is4xxClientError() ? "client_error" : "server_error");
                }
            } catch (Exception e) {
                // 忽略span处理错误，不影响主要业务逻辑
                System.err.println("Warning: Failed to add error attributes to span: " + e.getMessage());
            }

            HttpStatusCode statusCode = response.getStatusCode();
            if (statusCode.equals(HttpStatus.NOT_FOUND)) {
                throw new RuntimeException("Resource not found: " + response.getStatusText());
            } else if (statusCode.equals(HttpStatus.UNAUTHORIZED)) {
                throw new RuntimeException("Unauthorized access: " + response.getStatusText());
            } else if (statusCode.is5xxServerError()) {
                throw new RuntimeException("Server error: " + response.getStatusCode() + " " + response.getStatusText());
            }

            throw new RuntimeException("HTTP error: " + response.getStatusCode() + " " + response.getStatusText());
        }
    }
}