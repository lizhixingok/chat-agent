package com.zx.chat.agent.api.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 新建 demo 记录的入参。
 *
 * <p>校验消息直接写中文字面量。不用 {@code {key}} 形式 —— 那要靠 MessageSource 查表，
 * 本服务不做多语言，查表只是多一层间接。
 */
@Data
public class DemoCreateReqDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 名称，必填 */
    @NotBlank(message = "不能为空")
    private String name;

    /**
     * 分数，1~100。
     *
     * <p>上下界各配一条文案，不共用一条「必须在 X 和 Y 之间」：@Min 和 @Max 是两个独立约束，
     * 各自只能拿到自己的 value，凑不出另一半边界。
     *
     * <p>{value} 由 Hibernate Validator 按约束属性名插值，认 {value} 不认 {0}。
     */
    @Min(value = 1, message = "不能小于 {value}")
    @Max(value = 100, message = "不能大于 {value}")
    private Integer score;
}
