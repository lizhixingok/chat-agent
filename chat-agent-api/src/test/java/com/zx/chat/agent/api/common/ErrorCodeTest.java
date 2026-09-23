package com.zx.chat.agent.api.common;

import com.i61.common.bean.exception.BaseResultCode;
import com.zx.chat.agent.api.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    void codesAreUniqueAcrossAllEnumConstants() {
        Set<Integer> seen = new HashSet<>();
        for (ErrorCode ec : ErrorCode.values()) {
            assertThat(seen.add(ec.getCode()))
                .as("duplicate code %d on %s", ec.getCode(), ec.name())
                .isTrue();
        }
    }

    @Test
    void everyCodeHasNonBlankMessage() {
        for (ErrorCode ec : ErrorCode.values()) {
            assertThat(ec.getMessage())
                .as("message of %s", ec.name())
                .isNotBlank();
        }
    }

    @Test
    void containsNoSuccessCodeBecauseSuccessIsNotAnError() {
        // 成功走 RespResult.succeed()，不经过 ErrorCode。
        // 放个 SUCCESS 进来会让 new BizException(ErrorCode.SUCCESS) 这种荒谬写法能编译
        for (ErrorCode ec : ErrorCode.values()) {
            assertThat(ec.getCode())
                .as("%s 不该使用成功码", ec.name())
                .isNotEqualTo(BaseResultCode.SUCCESS_CODE);
        }
    }

    @Test
    void reusesBaseResultCodeNumbersForCommonCases() {
        assertThat(ErrorCode.SYSTEM_ERROR.getCode()).isEqualTo(BaseResultCode.BASE_ERROR_CODE);
        assertThat(ErrorCode.PARAM_INVALID.getCode()).isEqualTo(BaseResultCode.VALIDATE_ERROR_CODE);
        assertThat(ErrorCode.UNAUTHORIZED.getCode()).isEqualTo(BaseResultCode.AUTH_FAILD_CODE);
        assertThat(ErrorCode.FORBIDDEN.getCode()).isEqualTo(BaseResultCode.FORBIDDEN_CODE);
    }

    @Test
    void bizExceptionCarriesErrorCodeAndArgs() {
        BizException ex = new BizException(ErrorCode.RESOURCE_NOT_FOUND, "prize", 42L);

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(ex.getArgs()).containsExactly("prize", 42L);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void bizExceptionWithoutArgsHasEmptyArray() {
        BizException ex = new BizException(ErrorCode.SYSTEM_ERROR);

        assertThat(ex.getArgs()).isEmpty();
    }

    @Test
    void bizExceptionMessageIsTheErrorCodeText() {
        BizException ex = new BizException(ErrorCode.LOCK_ACQUIRE_FAILED);

        assertThat(ex.getMessage()).isEqualTo("获取锁失败，请重试");
    }

    @Test
    void bizExceptionFillsPlaceholdersIntoMessage() {
        BizException ex = new BizException(ErrorCode.RESOURCE_NOT_FOUND, "奖品", 42L);

        assertThat(ex.getMessage()).isEqualTo("奖品 不存在：42");
    }

    /** 占位符个数对不上时不能抛异常，否则会盖掉真正的业务错误 */
    @Test
    void argsMismatchFallsBackToRawTemplate() {
        BizException ex = new BizException(ErrorCode.RESOURCE_NOT_FOUND, "奖品");

        assertThat(ex.getMessage()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getMessage());
    }

    @Test
    void allMessagesAreDistinct() {
        long distinct = Arrays.stream(ErrorCode.values())
            .map(ErrorCode::getMessage)
            .distinct()
            .count();

        assertThat(distinct).isEqualTo(ErrorCode.values().length);
    }
}
