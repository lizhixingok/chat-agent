package com.zx.chat.agent.provider.controller.outer;

import com.i61.common.bean.bean.RespResult;
import com.zx.chat.agent.api.dto.req.ChatReqDTO;
import com.zx.chat.agent.api.dto.resp.ChatRespDTO;
import com.zx.chat.agent.api.exception.BizException;
import com.zx.chat.agent.service.service.AiChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/o/v1/chat")
@Tag(name = "AI Chat", description = "DeepSeek 对话接口")
public class AiChatController {

    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping("/completions")
    @Operation(summary = "同步对话")
    public RespResult<ChatRespDTO> chat(@Valid @RequestBody ChatReqDTO req) {
        return RespResult.succeed(new ChatRespDTO(aiChatService.chat(req.getMessage())));
    }

    @PostMapping(value = "/completions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式对话")
    public Flux<ServerSentEvent<String>> stream(@Valid @RequestBody ChatReqDTO req, HttpServletResponse response) {
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8");
        return Flux.defer(() -> aiChatService.stream(req.getMessage()))
            .map(chunk -> ServerSentEvent.builder(chunk).event("message").build())
            .onErrorResume(BizException.class, ex -> Flux.just(
                ServerSentEvent.builder("AI 服务暂时不可用").event("error").build()));
    }
}
