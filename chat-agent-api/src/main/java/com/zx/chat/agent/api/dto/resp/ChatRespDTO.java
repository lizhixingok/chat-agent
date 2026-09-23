package com.zx.chat.agent.api.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRespDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String content;
}
