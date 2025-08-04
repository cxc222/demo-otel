package com.demo.otel;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Scope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;

/**
 * Hutool HTTP工具类的OpenTelemetry包装器
 * 为Hutool HTTP请求添加分布式追踪支持
 */
@Component
public class TracedHutoolHttpUtil {

    private final Tracer tracer;

    @Autowired
    public TracedHutoolHttpUtil(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer("hutool-http-client", "1.0.0");
    }

    /**
     * 执行GET请求
     */
    public String get(String url) {
        return executeWithTracing("GET", url, null, null, () -> HttpUtil.get(url));
    }

    /**
     * 执行GET请求带参数
     */
    public String get(String url, Map<String, Object> params) {
        return executeWithTracing("GET", url, params, null, () -> HttpUtil.get(url, params));
    }

    /**
     * 执行POST请求
     */
    public String post(String url, String body) {
        return executeWithTracing("POST", url, null, body, () -> HttpUtil.post(url, body));
    }

    /**
     * 执行POST请求带参数
     */
    public String post(String url, Map<String, Object> params) {
        return executeWithTracing("POST", url, params, null, () -> HttpUtil.post(url, params));
    }

    /**
     * 执行PUT请求
     */
   /* public String put(String url, String body) {
        return executeWithTracing("PUT", url, null, body, () -> HttpUtil.put(url, body));
    }*/

    /**
     * 执行DELETE请求
     */
    /*public String delete(String url) {
        return executeWithTracing("DELETE", url, null, null, () -> HttpUtil.delete(url));
    }*/

    /**
     * 创建带追踪的HttpRequest
     */
    public TracedHttpRequest createRequest(String method, String url) {
        return new TracedHttpRequest(method, url, tracer);
    }

    /**
     * 执行HTTP请求的通用方法，添加追踪
     */
    private String executeWithTracing(String method, String url, Map<String, Object> params,
                                      String body, HttpSupplier<String> supplier) {
        URI uri = URI.create(url);

        SpanBuilder spanBuilder = tracer.spanBuilder("HTTP " + method)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(SemanticAttributes.HTTP_METHOD, method)
                .setAttribute(SemanticAttributes.HTTP_URL, url)
                .setAttribute(SemanticAttributes.HTTP_SCHEME, uri.getScheme())
                .setAttribute(SemanticAttributes.NET_PEER_NAME, uri.getHost())
                .setAttribute(SemanticAttributes.NET_PEER_PORT, uri.getPort() != -1 ? uri.getPort() :
                        ("https".equals(uri.getScheme()) ? 443 : 80))
                .setAttribute("http.client", "hutool");

        if (params != null && !params.isEmpty()) {
            spanBuilder.setAttribute("http.request.params.count", params.size());
        }

        if (body != null) {
            spanBuilder.setAttribute("http.request.body.size", body.length());
        }

        Span span = spanBuilder.startSpan();

        try (Scope scope = span.makeCurrent()) {
            String result = supplier.get();

            span.setStatus(StatusCode.OK);
            if (result != null) {
                span.setAttribute("http.response.body.size", result.length());
            }

            return result;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw new RuntimeException(e);
        } finally {
            span.end();
        }
    }

    @FunctionalInterface
    private interface HttpSupplier<T> {
        T get() throws Exception;
    }

    /**
     * 带追踪的HttpRequest包装器
     */
    public static class TracedHttpRequest {
        private final HttpRequest httpRequest;
        private final Tracer tracer;
        private final String method;
        private final String url;

        public TracedHttpRequest(String method, String url, Tracer tracer) {
            this.httpRequest = HttpUtil.createRequest(cn.hutool.http.Method.valueOf(method.toUpperCase()), url);
            this.tracer = tracer;
            this.method = method;
            this.url = url;
        }

        public TracedHttpRequest header(String name, String value) {
            httpRequest.header(name, value);
            return this;
        }

        public TracedHttpRequest body(String body) {
            httpRequest.body(body);
            return this;
        }

        public TracedHttpRequest form(Map<String, Object> form) {
            httpRequest.form(form);
            return this;
        }

        public TracedHttpRequest timeout(int timeout) {
            httpRequest.timeout(timeout);
            return this;
        }

        /**
         * 执行请求并返回追踪的响应
         */
        public String execute() {
            URI uri = URI.create(url);

            Span span = tracer.spanBuilder("HTTP " + method)
                    .setSpanKind(SpanKind.CLIENT)
                    .setAttribute(SemanticAttributes.HTTP_METHOD, method)
                    .setAttribute(SemanticAttributes.HTTP_URL, url)
                    .setAttribute(SemanticAttributes.HTTP_SCHEME, uri.getScheme())
                    .setAttribute(SemanticAttributes.NET_PEER_NAME, uri.getHost())
                    .setAttribute(SemanticAttributes.NET_PEER_PORT, uri.getPort() != -1 ? uri.getPort() :
                            ("https".equals(uri.getScheme()) ? 443 : 80))
                    .setAttribute("http.client", "hutool")
                    .startSpan();

            try (Scope scope = span.makeCurrent()) {
                // 将追踪上下文注入到HTTP头中
                injectTraceContext(span);

                HttpResponse response = httpRequest.execute();

                span.setAttribute(SemanticAttributes.HTTP_STATUS_CODE, response.getStatus());

                if (response.isOk()) {
                    span.setStatus(StatusCode.OK);
                } else {
                    span.setStatus(StatusCode.ERROR, "HTTP " + response.getStatus());
                }

                String body = response.body();
                if (body != null) {
                    span.setAttribute("http.response.body.size", body.length());
                }

                return body;
            } catch (Exception e) {
                span.setStatus(StatusCode.ERROR, e.getMessage());
                span.recordException(e);
                throw e;
            } finally {
                span.end();
            }
        }

        /**
         * 将追踪上下文注入到HTTP头中
         */
        private void injectTraceContext(Span span) {
            // 手动注入W3C Trace Context
            String traceId = span.getSpanContext().getTraceId();
            String spanId = span.getSpanContext().getSpanId();
            String traceFlags = span.getSpanContext().getTraceFlags().asHex();

            String traceparent = String.format("00-%s-%s-%s", traceId, spanId, traceFlags);
            httpRequest.header("traceparent", traceparent);
        }
    }
}
