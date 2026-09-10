package com.backend.lifeplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 本地生活平台后端服务启动入口。 */
@SpringBootApplication
@EnableScheduling
public class LifePlatformApplication {

    /** 启动并初始化 Spring 应用。 */
    public static void main(String[] args) {
        SpringApplication.run(LifePlatformApplication.class, args);
    }

}
