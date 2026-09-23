package com.zx.chat.agent.service.service.impl;

import com.zx.chat.agent.api.dto.req.DemoCreateReqDTO;
import com.zx.chat.agent.api.dto.resp.DemoRespDTO;
import com.zx.chat.agent.api.exception.BizException;
import com.zx.chat.agent.service.service.DemoService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * demo 业务实现。数据放内存，**不接数据库** —— 这个类的目的是让 HTTP 链路
 * （filter → controller → service → 全局异常处理 → 响应序列化）能独立跑通，
 * 不因 DB 连不上而失败。真实业务请另建 Service 走 mapper。
 *
 * <p>进程重启数据即丢失，这是刻意的，不要当存储用。
 */
@Service
public class DemoServiceImpl implements DemoService {

    /** 自增 id 发号器，从 1 开始 */
    private final AtomicLong idGen = new AtomicLong(0);

    private final Map<Long, DemoRespDTO> store = new ConcurrentHashMap<>();

    @Override
    public DemoRespDTO create(DemoCreateReqDTO req) {
        DemoRespDTO resp = new DemoRespDTO();
        resp.setId(idGen.incrementAndGet());
        resp.setName(req.getName());
        resp.setScore(req.getScore());
        // 必须显式指定 UTC，无参 now() 取的是 JVM 本地时间，会被 UtcTimeGuardTest 拦下
        resp.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        store.put(resp.getId(), resp);
        return resp;
    }

    @Override
    public DemoRespDTO findById(Long id) {
        DemoRespDTO resp = store.get(id);
        if (resp == null) {
            throw new BizException("Demo 不存在：" + id);
        }
        return resp;
    }

    @Override
    public List<DemoRespDTO> listAll() {
        return store.values().stream()
            .sorted(Comparator.comparing(DemoRespDTO::getId))
            .toList();
    }
}
