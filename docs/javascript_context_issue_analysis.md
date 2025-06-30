# JavaScript Context线程安全问题分析

## 问题概述

在Spark Core项目中遇到的`RuntimeException: No Context associated with current Thread`错误是Rhino JavaScript引擎的典型线程安全问题。

## 错误表现

```
java.lang.RuntimeException: No Context associated with current Thread
	at org.mozilla.javascript.Context.getContext(Context.java:2495)
	at org.mozilla.javascript.SecurityController.createLoader(SecurityController.java:90)
	at org.mozilla.javascript.optimizer.Codegen.defineClass(Codegen.java:137)
	at org.mozilla.javascript.optimizer.Codegen.createScriptObject(Codegen.java:101)
	at org.mozilla.javascript.Context.compileImpl(Context.java:2578)
	at org.mozilla.javascript.Context.compileString(Context.java:1481)
	at org.mozilla.javascript.Context.evaluateString(Context.java:1236)
```

## 根本原因

### 1. Rhino JavaScript引擎的线程模型
- **Thread-Local Context**: Rhino使用ThreadLocal存储Context，每个线程必须有自己的Context
- **Context生命周期管理**: Context必须在使用前通过`Context.enter()`创建，使用后通过`Context.exit()`释放
- **线程切换问题**: 当JavaScript代码在不同线程间执行时，原有Context不再可用

### 2. 原始代码的问题

```kotlin
// 问题代码
abstract class SparkJS {
    val context = Context.enter()  // ❌ 在类初始化时就创建Context
    val scope = context.initStandardObjects()
    
    fun eval(script: String, contextName: String = "") {
        context.evaluateString(scope, script, contextName, 1, null)  // ❌ 直接使用可能已失效的Context
    }
}
```

**问题分析**：
1. **时机错误**: 在类构造时创建Context，但实际使用可能在不同线程
2. **生命周期管理缺失**: 没有配对的`Context.exit()`调用
3. **线程安全问题**: 多个线程可能同时访问同一个Context实例

### 3. 触发场景
- **文件热重载**: 文件监控线程触发脚本重新加载
- **网络同步**: 网络包处理线程执行脚本同步
- **异步任务**: 各种异步任务中执行JavaScript代码

## 解决方案

### 修复后的代码

```kotlin
abstract class SparkJS {
    @Volatile
    var context: Context? = null
        private set
    @Volatile
    var scope: org.mozilla.javascript.Scriptable? = null
        private set

    fun eval(script: String, contextName: String = "") {
        runCatching {
            // 为当前线程创建或获取Context
            val currentContext = Context.enter()
            try {
                // 如果scope还没有初始化或者Context发生了变化，重新初始化
                if (scope == null || context != currentContext) {
                    context = currentContext
                    scope = currentContext.initStandardObjects()
                }
                
                currentContext.evaluateString(scope, script, contextName, 1, null)
            } finally {
                Context.exit()  // ✅ 确保Context被正确释放
            }
        }.getOrElse { throw LoaderException("Js脚本加载失败: $contextName", it) }
    }
}
```

### 关键改进点

1. **延迟初始化**: Context在实际使用时才创建
2. **线程安全**: 使用`@Volatile`确保多线程可见性
3. **正确的生命周期管理**: 每次使用都有配对的enter/exit
4. **异常安全**: 使用try-finally确保Context总是被释放
5. **Context重用**: 检查Context是否发生变化，避免不必要的重建

## 最佳实践

### 1. Context使用模式
```kotlin
// ✅ 正确模式
val context = Context.enter()
try {
    // JavaScript操作
    context.evaluateString(scope, script, name, 1, null)
} finally {
    Context.exit()  // 必须在finally中释放
}
```

### 2. 多线程考虑
- 每个线程需要自己的Context
- 不要在线程间共享Context实例
- 使用ThreadLocal或在每次使用时重新创建

### 3. 错误处理
- 始终在finally块中调用`Context.exit()`
- 处理Context创建失败的情况
- 提供清晰的错误信息

## 相关问题和注意事项

### 1. 性能考虑
- 频繁的Context创建/销毁有一定开销
- 可以考虑ThreadLocal Context池优化
- 避免在热路径中重复创建Scope

### 2. 内存泄漏预防
- 未正确释放的Context会导致内存泄漏
- 确保所有代码路径都有Context.exit()调用
- 注意异常情况下的资源清理

### 3. 调试技巧
- 使用Context.getCurrentContext()检查当前线程Context状态
- 记录Context创建和销毁的调用栈
- 监控JavaScript引擎的线程使用情况

## 验证方法

1. **多线程测试**: 在不同线程中并发执行JavaScript代码
2. **热重载测试**: 频繁修改脚本文件触发重新加载
3. **网络同步测试**: 客户端/服务端脚本同步场景
4. **长时间运行测试**: 确保没有内存泄漏

## 总结

这个问题的核心是理解Rhino JavaScript引擎的线程模型和Context生命周期管理。通过正确的资源管理和线程安全设计，可以完全避免这类问题。在多线程环境中使用JavaScript引擎时，必须严格遵循Context的使用规范。

## 5. Skill 未定义（全局组件丢失）问题补充

### 5.1 现象
- 客户端或服务端在执行 JS 脚本时抛出 `ReferenceError: "Skill" 未定义`。
- 说明脚本在全局作用域无法解析到 `Skill` 组件（或其他 API 组件）。

### 5.2 根因
1. **事件时序差异**：`SparkJS.register()` 通过事件总线异步注入组件；若在 `scope` 尚未创建时触发，则 `scope.put("Skill", …)` 失败，组件丢失。
2. **scope 被重复初始化**：`SparkJS.eval()` 旧实现每次进入新 `Context` 都执行 `initStandardObjects()`，会覆盖之前已注入的全局对象，导致组件再次丢失。

### 5.3 解决方案
| 修复点 | 代码位置 | 说明 |
| ------ | -------- | ---- |
| 提前初始化 `scope` | `SparkJsApplier.onClientPlayerLogin` | 在真正执行脚本前手动创建 `Context + scope`，保证后续组件注入安全 |
| 避免重复初始化 `scope` | `SparkJS.eval()` | 仅在 `scope == null` 时调用 `initStandardObjects()`，防止后续调用覆盖 |
| 强制注入关键组件 | `SparkJsApplier.onClientPlayerLogin` | Fallback：`scope.put("Skill", scope, JSSkillApi)` 保障组件始终可用 |

### 5.4 结果
- `Skill` 等 API 组件在脚本执行阶段始终可解析。  
- 消除了 `ReferenceError: "Skill" 未定义` / `TypeError: 找不到函数 …` 错误。

---

*本文档同步更新于 2025-06-28，涵盖 Context 生命周期、线程安全及全局组件丢失的综合解决方案。* 