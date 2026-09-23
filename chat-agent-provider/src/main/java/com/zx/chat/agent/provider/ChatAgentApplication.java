package com.zx.chat.agent.provider;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.i61", "com.zx.chat.agent"})
@EnableFeignClients(basePackages = "com.i61")
@EnableDiscoveryClient
@MapperScan(basePackages = "com.i61.**.mapper")
public class ChatAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatAgentApplication.class, args);
    }
}
