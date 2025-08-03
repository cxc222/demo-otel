package com.demo.otel;

import io.opentelemetry.api.OpenTelemetry;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Configuration
public class HttpClientInstrumentationConfig {
    @Autowired
    private OpenTelemetry openTelemetry;

    /**
     * 配置OkHttp客户端 - 推荐使用
     */
    @Bean
    @Primary
    public OkHttpClient instrumentedOkHttpClient() {
        OkHttpTelemetry okHttpTelemetry = OkHttpTelemetry.builder(openTelemetry)
                .setCapturedRequestHeaders("Authorization", "Content-Type", "Accept")
                .setCapturedResponseHeaders("Content-Type", "Content-Length")
                .build();

        return new OkHttpClient.Builder()
                .addInterceptor(okHttpTelemetry.newInterceptor())
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
                .clientConnector(new ClientHttpConnector() {
                    @Override
                    public Mono<ClientHttpResponse> connect(
                            org.springframework.http.HttpMethod method,
                            java.net.URI uri,
                            org.springframework.http.client.ClientHttpRequest request) {
                        // WebClient会自动通过Micrometer集成获得追踪
                        return null;
                    }
                })
                .build();
    }

    /**
     * Apache HttpClient 4.x配置 (如果需要使用)
     */
    @Bean
    public CloseableHttpClient instrumentedApacheHttpClient4() {
        ApacheHttpClientTelemetry telemetry = ApacheHttpClientTelemetry.builder(openTelemetry)
                .setCapturedRequestHeaders("Authorization", "Content-Type")
                .setCapturedResponseHeaders("Content-Type", "Content-Length")
                .build();

        return telemetry.newHttpClientBuilder()
                .setMaxConnTotal(100)
                .setMaxConnPerRoute(20)
                .build();
    }

    /**
     * Apache HttpClient 5.x配置 (如果需要使用)
     */
    @Bean
    public CloseableHttpClient instrumentedApacheHttpClient5() {
        ApacheHttpClient5Telemetry telemetry = ApacheHttpClient5Telemetry.builder(openTelemetry)
                .setCapturedRequestHeaders("Authorization", "Content-Type")
                .setCapturedResponseHeaders("Content-Type", "Content-Length")
                .build();

        return telemetry.newHttpClientBuilder()
                .setMaxConnTotal(100)
                .setMaxConnPerRoute(20)
                .build();
    }
}
