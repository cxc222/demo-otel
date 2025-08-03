package com.demo.otel;

import io.opentelemetry.api.trace.Span;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/http")
public class HttpClientController {

    @Autowired
    private HttpClientExampleService httpClientService;

    @GetMapping("/rest-template")
    public String testRestTemplate() {
        Span.current().setAttribute("client.type", "rest-template");
        return httpClientService.callExternalApiWithRestTemplate();
    }

    @GetMapping("/webclient")
    public Mono<String> testWebClient() {
        Span.current().setAttribute("client.type", "webclient");
        return httpClientService.callExternalApiWithWebClient();
    }

    @GetMapping("/okhttp")
    public String testOkHttp() throws Exception {
        Span.current().setAttribute("client.type", "okhttp");
        return httpClientService.callExternalApiWithOkHttp();
    }

    @GetMapping("/hutool")
    public String testHutool() {
        Span.current().setAttribute("client.type", "hutool");
        return httpClientService.callExternalApiWithHutool();
    }

    @GetMapping("/concurrent")
    public Map<String, String> testConcurrentCalls() {
        Span.current().setAttribute("operation.type", "concurrent-calls");
        return httpClientService.callMultipleApisAsync();
    }
}