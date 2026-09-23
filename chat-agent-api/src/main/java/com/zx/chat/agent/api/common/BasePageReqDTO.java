package com.zx.chat.agent.api.common;

import lombok.Data;

@Data
public class BasePageReqDTO implements java.io.Serializable {

    private int pageNum = 1;

    private int pageSize = 20;
}
