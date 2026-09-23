package com.zx.chat.agent.api.dto.resp;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * demo 记录的出参。
 *
 * <p>id 是 Long，JacksonConfig 会序列化成字符串，避免前端丢精度。
 * createdAt 是 LocalDateTime，按全项目约定视为 UTC。
 */
@Data
public class DemoRespDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private Integer score;

    /** 创建时间，UTC。输出格式 yyyy-MM-dd HH:mm:ss */
    private LocalDateTime createdAt;
}
