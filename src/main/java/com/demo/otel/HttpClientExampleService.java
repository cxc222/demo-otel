package com.demo.otel;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class HttpClientExampleService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private WebClient webClient;

    @Autowired
    private OkHttpClient okHttpClient;

    @Autowired
    private TracedHutoolHttpUtil tracedHutoolHttpUtil;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 使用RestTemplate发送HTTP请求 (推荐用于同步调用)
     */
    public String callExternalApiWithRestTemplate() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer token");
        headers.set("Content-Type", "application/json");

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("key", "value");

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "https://api.example.com/data",
                HttpMethod.POST,
                entity,
                String.class
        );

        return response.getBody();
    }

    /**
     * 使用WebClient发送异步HTTP请求 (推荐用于响应式编程)
     */
    public Mono<String> callExternalApiWithWebClient() {
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("key", "value");

        return webClient
                .post()
                .uri("https://api.example.com/data")
                .header("Authorization", "Bearer token")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class);
    }

    /**
     * 使用OkHttp发送HTTP请求 (推荐用于需要细粒度控制)
     */
    public String callExternalApiWithOkHttp() throws IOException {
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("key", "value");

        RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(requestBody),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url("https://api.example.com/data")
                .post(body)
                .addHeader("Authorization", "Bearer token")
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.body() != null) {
                return response.body().string();
            }
            return null;
        }
    }

    /**
     * 使用TracedHutoolHttpUtil发送HTTP请求 (兼容现有Hutool代码)
     */
    public String callExternalApiWithHutool() {
        // 简单GET请求
        String getResult = tracedHutoolHttpUtil.get("https://api.example.com/data");

        // POST请求带参数
        Map<String, Object> params = new HashMap<>();
        params.put("key", "value");
        String postResult = tracedHutoolHttpUtil.post("https://api.example.com/data", params);

        // 复杂请求构建
        String complexResult = tracedHutoolHttpUtil
                .createRequest("POST", "https://api.example.com/data")
                .header("Authorization", "Bearer token")
                .header("Content-Type", "application/json")
                .body("{\"key\":\"value\"}")
                .timeout(30000)
                .execute();

        return complexResult;
    }

    /**
     * 并发HTTP请求示例
     */
    public Map<String, String> callMultipleApisAsync() {
        // 使用WebClient进行并发调用
        Mono<String> api1 = webClient.get()
                .uri("https://api1.example.com/data")
                .retrieve()
                .bodyToMono(String.class);

        Mono<String> api2 = webClient.get()
                .uri("https://api2.example.com/data")
                .retrieve()
                .bodyToMono(String.class);

        Mono<String> api3 = webClient.get()
                .uri("https://api3.example.com/data")
                .retrieve()
                .bodyToMono(String.class);

        // 并发执行并合并结果
        return Mono.zip(api1, api2, api3)
                .map(tuple -> {
                    Map<String, String> results = new HashMap<>();
                    results.put("api1", tuple.getT1());
                    results.put("api2", tuple.getT2());
                    results.put("api3", tuple.getT3());
                    return results;
                })
                .block(); // 阻塞等待结果，实际使用中建议返回Mono
    }

    /**
     * 错误处理和重试示例
     */
    public String callApiWithRetry() {
        return webClient
                .get()
                .uri("https://api.example.com/data")
                .retrieve()
                .bodyToMono(String.class)
                .retry(3) // 重试3次
                .onErrorReturn("fallback response") // 失败时返回默认值
                .block();
    }

    /**
     * 流式处理大响应示例
     */
    public Mono<Void> streamLargeResponse() {
        return webClient
                .get()
                .uri("https://api.example.com/large-data")
                .retrieve()
                .bodyToFlux(String.class)
                .doOnNext(chunk -> {
                    // 处理每个数据块
                    System.out.println("Received chunk: " + chunk);
                })
                .then();
    }
}
