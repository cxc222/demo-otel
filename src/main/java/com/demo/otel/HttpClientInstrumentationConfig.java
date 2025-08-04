package com.demo.otel;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.okhttp.v3_0.OkHttpTelemetry;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

@Configuration
public class HttpClientInstrumentationConfig {
    @Autowired
    private OpenTelemetry openTelemetry;

    /**
     * 配置OkHttp客户端 - 使用新的API
     */
    @Bean
    @Primary
    public OkHttpClient instrumentedOkHttpClient() {
        // 使用新的API创建OkHttp telemetry
        OkHttpTelemetry okHttpTelemetry = OkHttpTelemetry.builder(openTelemetry)
                .setCapturedRequestHeaders("Authorization", "Content-Type", "Accept")
                .setCapturedResponseHeaders("Content-Type", "Content-Length")
                .build();

        // 使用新的API创建instrumented的OkHttpClient
        return okHttpTelemetry.newClientBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(30))
                .writeTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * 配置RestTemplate使用OkHttp
     */
    @Bean
    @Primary
    public RestTemplate instrumentedRestTemplate(OkHttpClient okHttpClient) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(new OkHttp3ClientHttpRequestFactory(okHttpClient));
        return restTemplate;
    }

    /**
     * 配置WebClient - 响应式HTTP客户端
     */
    @Bean
    public WebClient instrumentedWebClient(OkHttpClient okHttpClient) {
        return WebClient.builder()
                .codecs(configurer -> {
                    // 配置最大内存大小
                    configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024); // 10MB
                })
                .build();
    }
}
