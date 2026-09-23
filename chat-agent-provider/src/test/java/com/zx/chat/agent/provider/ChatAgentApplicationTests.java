package com.zx.chat.agent.provider;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 容器启动冒烟测试。
 *
 * <p>已禁用：启动完整容器需要真实的 MySQL、Apollo 配置中心与 Redis，
 * 本地与 CI 都不具备这些外部依赖，跑起来必然因 UnknownHostException 失败。
 * 需要验证容器能否正常启动时，在联调环境手动去掉 @Disabled 执行。
 */
@Disabled("需要真实 MySQL/Apollo/Redis，本地与 CI 无依赖环境跑不通")
@SpringBootTest
class ChatAgentApplicationTests {

    @Test
    void contextLoads() {
    }

}
