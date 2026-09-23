package com.zx.chat.agent.api.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class ChatReqDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "不能为空")
    @Size(max = 4000, message = "长度不能超过 {max}")
    private String message;
}
