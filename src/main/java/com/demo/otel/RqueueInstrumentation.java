package com.demo.otel;

import com.github.sonus21.rqueue.core.RqueueMessage;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Scope;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RqueueInstrumentation {

    private final Tracer tracer;

    @Autowired
    public RqueueInstrumentation(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer("rqueue-instrumentation", "1.0.0");
    }

    // 拦截RQueue消息处理
    @Around("@annotation(com.github.sonus21.rqueue.annotation.RqueueListener)")
    public Object instrumentRqueueListener(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();

        SpanBuilder spanBuilder = tracer.spanBuilder("rqueue.message.process")
                .setSpanKind(SpanKind.CONSUMER)
                .setAttribute("component", "rqueue")
                .setAttribute("operation", "message.process")
                .setAttribute("method.name", methodName)
                .setAttribute("class.name", className);

        // 尝试从参数中获取消息信息
        Object[] args = joinPoint.getArgs();
        if (args.length > 0 && args[0] instanceof RqueueMessage) {
            RqueueMessage message = (RqueueMessage) args[0];
            spanBuilder.setAttribute("rqueue.message.id", message.getId())
                    .setAttribute("rqueue.queue.name", message.getQueueName())
                    .setAttribute("rqueue.retry.count", message.getRetryCount());
        }

        Span span = spanBuilder.startSpan();
        try (Scope scope = span.makeCurrent()) {
            Object result = joinPoint.proceed();
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    // 拦截RQueue消息发送/入队操作
    @Around("execution(* com.github.sonus21.rqueue.core.RqueueMessageEnqueuer.*(..))")
    public Object instrumentRqueueSender(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();

        SpanBuilder spanBuilder = tracer.spanBuilder("rqueue.message.send")
                .setSpanKind(SpanKind.PRODUCER)
                .setAttribute("component", "rqueue")
                .setAttribute("operation", "message.send")
                .setAttribute("method.name", methodName);

        // 尝试从参数中获取队列名称
        Object[] args = joinPoint.getArgs();
        if (args.length > 0 && args[0] instanceof String) {
            spanBuilder.setAttribute("rqueue.queue.name", (String) args[0]);
        }

        Span span = spanBuilder.startSpan();
        try (Scope scope = span.makeCurrent()) {
            Object result = joinPoint.proceed();
            span.setStatus(StatusCode.OK);

            // 如果返回消息ID，记录它
            if (result instanceof String) {
                span.setAttribute("rqueue.message.id", (String) result);
            }

            return result;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
