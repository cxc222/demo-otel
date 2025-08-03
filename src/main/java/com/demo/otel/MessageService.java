package com.demo.otel;

import com.github.sonus21.rqueue.annotation.RqueueListener;
import com.github.sonus21.rqueue.core.RqueueMessageEnqueuer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MessageService {

    @Autowired
    private RqueueMessageEnqueuer rqueueMessageEnqueuer;

    @Autowired
    private Tracer tracer;

    // 发送消息 - 会被自动instrumented
    public void sendMessage(String queueName, Object message) {
        rqueueMessageEnqueuer.enqueue(queueName, message);
    }

    // 处理消息 - 会被自动instrumented
    @RqueueListener(value = "my-queue", numRetries = "3")
    public void processMessage(String message) {
        // 业务逻辑处理
        System.out.println("Processing message: " + message);

        // 可以手动添加自定义span
        Span span = tracer.spanBuilder("custom.business.logic")
                .setAttribute("message.content", message)
                .startSpan();

        try {
            // 模拟业务处理
            Thread.sleep(100);
        } catch (InterruptedException e) {
            span.recordException(e);
            Thread.currentThread().interrupt();
        } finally {
            span.end();
        }
    }
}
