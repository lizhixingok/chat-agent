package com.zx.chat.agent.service.service.impl;

import com.zx.chat.agent.api.exception.BizException;
import com.zx.chat.agent.service.service.AiChatService;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AiChatServiceImpl implements AiChatService {

    private final ChatClient chatClient;
    private final StreamingChatClient streamingChatClient;

    public AiChatServiceImpl(ChatClient chatClient, StreamingChatClient streamingChatClient) {
        this.chatClient = chatClient;
        this.streamingChatClient = streamingChatClient;
    }

    @Override
    public String chat(String message) {
        try {
            return chatClient.call(message);
        } catch (RuntimeException ex) {
            throw upstreamFailure();
        }
    }

    @Override
    public Flux<String> stream(String message) {
        try {
            return streamingChatClient.stream(message)
                .filter(chunk -> chunk != null && !chunk.isEmpty())
                .onErrorMap(RuntimeException.class, ex -> upstreamFailure());
        } catch (RuntimeException ex) {
            throw upstreamFailure();
        }
    }

    /** 上游模型调用失败。原始异常里可能带密钥、请求体，一律不外泄，只回通用文案。 */
    private BizException upstreamFailure() {
        return new BizException("第三方服务调用失败");
    }
}
