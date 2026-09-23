package com.zx.chat.agent.provider.controller.outer;

import com.i61.common.bean.bean.RespResult;
import com.zx.chat.agent.api.dto.req.DemoCreateReqDTO;
import com.zx.chat.agent.api.dto.resp.DemoRespDTO;
import com.zx.chat.agent.service.service.DemoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * demo 接口。用来验证脚手架的 HTTP 链路是否通：请求日志、
 * 参数校验、全局异常、统一响应包装、JSON 序列化规则。
 *
 * <p>约定：
 * <ul>
 *   <li>Controller 只做参数接收与响应包装，业务逻辑一律在 Service
 *   <li>不写 try-catch —— 异常交给 GlobalExceptionHandler 统一处理
 *   <li>成功走 {@code RespResult.succeed()}，失败靠抛 BizException
 * </ul>
 */
@RestController
@RequestMapping("/demo")
@Tag(name = "Demo", description = "脚手架链路验证接口")
public class DemoController {

    private final DemoService demoService;

    public DemoController(DemoService demoService) {
        this.demoService = demoService;
    }

    /** 最简连通性检查，不碰任何依赖，用来确认服务起来了 */
    @GetMapping("/ping")
    @Operation(summary = "连通性检查")
    public RespResult<String> ping() {
        return RespResult.succeed("pong");
    }

    /**
     * 新建记录。
     *
     * <p>@Valid 触发 DTO 上的校验注解，失败由 GlobalExceptionHandler
     * 转成 PARAM_INVALID，HTTP 仍是 200，错误在 body 的 code 里。
     */
    @PostMapping("/create")
    @Operation(summary = "新建记录")
    public RespResult<DemoRespDTO> create(@Valid @RequestBody DemoCreateReqDTO req) {
        return RespResult.succeed(demoService.create(req));
    }

    /** 按 id 查询。id 不存在时 Service 抛 BizException，返回 code 10001 */
    @GetMapping("/{id}")
    @Operation(summary = "按 id 查询")
    public RespResult<DemoRespDTO> findById(@PathVariable Long id) {
        return RespResult.succeed(demoService.findById(id));
    }

    /** 列出全部记录 */
    @GetMapping("/listAll")
    @Operation(summary = "查询全部")
    public RespResult<List<DemoRespDTO>> listAll() {
        return RespResult.succeed(demoService.listAll());
    }

    /** 故意抛未预期异常，验证兜底分支返回 HTTP 500 且不泄漏内部细节 */
    @GetMapping("/boom")
    @Operation(summary = "触发系统异常（验证兜底分支）")
    public RespResult<Void> boom() {
        throw new IllegalStateException("intentional failure for testing");
    }
}
