package com.demo.otel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy // 启用AOP，用于RQueue instrumentation
public class Application {

    public static void main(String[] args) {
        // 如果不使用Java Agent，可以在这里设置系统属性
        System.setProperty("otel.service.name", "my-spring-boot-app");
        System.setProperty("otel.resource.attributes", "service.version=1.0.0");

        SpringApplication.run(Application.class, args);
    }
}
