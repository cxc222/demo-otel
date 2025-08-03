package com.demo.otel;

import io.opentelemetry.api.trace.Span;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class MessageController {

    @Autowired
    private MessageService messageService;

    @PostMapping("/send")
    public String sendMessage(@RequestParam String queue, @RequestBody String message) {
        // 添加自定义属性到当前span
        Span.current().setAttribute("queue.name", queue);
        Span.current().setAttribute("message.size", message.length());

        messageService.sendMessage(queue, message);
        return "Message sent successfully";
    }
}
