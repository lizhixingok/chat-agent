package com.zx.chat.agent.service.service;

import com.zx.chat.agent.api.dto.req.DemoCreateReqDTO;
import com.zx.chat.agent.api.dto.resp.DemoRespDTO;

import java.util.List;

/**
 * demo 业务接口。用于验证 Controller → Service 链路与基础设施是否装配正确。
 */
public interface DemoService {

    /** 新建一条记录，返回带 id 与创建时间的完整对象 */
    DemoRespDTO create(DemoCreateReqDTO req);

    /** 按 id 查询，不存在时抛 BizException(RESOURCE_NOT_FOUND) */
    DemoRespDTO findById(Long id);

    /** 列出全部记录，按 id 升序 */
    List<DemoRespDTO> listAll();
}
