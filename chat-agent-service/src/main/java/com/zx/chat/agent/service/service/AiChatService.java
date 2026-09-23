package com.zx.chat.agent.service.service;

import reactor.core.publisher.Flux;

public interface AiChatService {

    String chat(String message);

    Flux<String> stream(String message);
}
