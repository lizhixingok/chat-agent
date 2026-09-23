# chat-agent 项目约定

## 错误码：只用 BaseResultCode

错误码一律用 `com.i61.common.bean.exception.BaseResultCode`，**禁止**新建 `ErrorCode` 之类的项目自有错误码枚举。

可用常量：

| 常量 | 值 | 用途 |
|---|---|---|
| `SUCCESS_CODE` | 0 | 成功，走 `RespResult.succeed()`，不经过异常 |
| `BASE_ERROR_CODE` | 101 | 通用业务错误，默认值 |
| `VALIDATE_ERROR_CODE` | 102 | 参数校验失败 |
| `AUTH_FAILD_CODE` | 401 | 未登录 / 登录态失效 |
| `FORBIDDEN_CODE` | 403 | 已登录但无权限 |

### 选码标准

只问一句：**调用方会不会因为这个码走不同的代码分支？**

- **会** → 用 `BaseResultCode` 里对应的明确码。典型就是登录态那几个：401 要跳登录页，403 要跳提示页。
- **不会** → 默认 101 + message。「手机号格式有误哦」「兑换课时不能为 0」这类，调用方只会 `toast(msg)`，为它们各编一个码是纯成本。

### 抛异常的写法

```java
// 调用方不分支，默认 101
throw new BizException("Demo 不存在：" + id);

// 调用方要分支，显式给码
throw new BizException(BaseResultCode.AUTH_FAILD_CODE, "未登录或登录已失效");
```

约束：

- 文案写在抛出点，中文写死（B 端不做多语言），带变量直接字符串拼接，不做 `%s` 模板 + args 渲染。
- 内部细节（lockKey、上游响应体、连接串、堆栈）只留在异常字段里供日志用，**不进对外 message**。
- 需要调用方按类型 catch 的场景（如 `LockAcquireFailedException`、`HttpClientException`），继承 `BizException` 新建异常类，靠**异常类型**区分，不靠错误码。
