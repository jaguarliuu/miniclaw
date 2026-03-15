package com.miniclaw;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MiniClaw 应用程序入口
 * 
 * 这是整个 AI Agent 框架的起点。
 * Spring Boot 会自动扫描当前包及子包下的所有组件。
 * 
 * @SpringBootApplication 是一个组合注解，包含：
 * - @SpringBootConfiguration：这是一个配置类
 * - @EnableAutoConfiguration：启用自动配置
 * - @ComponentScan：扫描当前包及子包的组件
 */
@SpringBootApplication
public class MiniClawApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniClawApplication.class, args);
    }
}
