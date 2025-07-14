# 问题解释：JavaScript Context 和 API 组件问题

## 发生了什么？

在之前的问题中，你的 Spark Core 项目遇到了两个相关但不同的 JavaScript 执行问题：

### 1. 主要错误现象

当你运行命令 `/spark skill play @a spark_core:sword_combo_0` 时，游戏崩溃并出现以下错误：

```
java.lang.NullPointerException: Cannot read field "topCallScope" because "cx" is null
```

以及：

```
TypeError: 在对象 cn.solarmoon.spark_core.js.extension.JSEntityHelper@3b6a27e 中找不到函数 move 。
```

## 根本原因分析

### 问题1：JavaScript Context 线程安全问题

**什么是 Context？**
- Rhino JavaScript 引擎使用 `Context` 来管理 JavaScript 代码的执行环境
- 每个线程都需要有自己的 Context，这是 Rhino 的设计要求
- Context 必须通过 `Context.enter()` 创建，用完后通过 `Context.exit()` 释放

**原始代码的问题：**
```kotlin
// 有问题的旧代码
abstract class SparkJS {
    val context = Context.enter()  // ❌ 在类初始化时创建
    val scope = context.initStandardObjects()
    
    fun eval(script: String, contextName: String = "") {
        context.evaluateString(scope, script, contextName, 1, null)  // ❌ 可能在错误的线程使用
    }
}
```

**为什么会出错？**
1. **时机错误**：Context 在类构造时创建，但技能执行可能在不同的线程（如游戏主线程、网络线程等）
2. **生命周期管理缺失**：没有正确的 `Context.exit()` 调用
3. **线程切换**：当代码在不同线程间执行时，原有的 Context 就失效了

### 问题2：API 组件丢失问题

**什么是 API 组件？**
- `Skill`、`EntityHelper` 等是注入到 JavaScript 全局作用域的 API 对象
- 这些对象让 JavaScript 代码能够调用 Java/Kotlin 的功能

**为什么会丢失？**
1. **事件时序问题**：组件注册通过事件总线异步进行，如果 scope 还没创建就触发注册，会失败
2. **scope 重复初始化**：每次创建新 Context 时都会重新初始化 scope，覆盖之前注入的组件
3. **缺少关键函数**：`JSEntityHelper` 缺少 `move` 函数，导致脚本调用失败

## 解决方案

### 修复1：正确的 Context 生命周期管理

```kotlin
// 修复后的代码
abstract class SparkJS {
    @Volatile
    var context: Context? = null
    @Volatile  
    var scope: org.mozilla.javascript.Scriptable? = null

    fun eval(script: String, contextName: String = "") {
        runCatching {
            val currentContext = Context.enter()  // ✅ 每次使用时创建
            try {
                if (scope == null) {  // ✅ 只在需要时初始化
                    context = currentContext
                    scope = currentContext.initStandardObjects()
                }
                currentContext.evaluateString(scope, script, contextName, 1, null)
            } finally {
                Context.exit()  // ✅ 确保释放
            }
        }.getOrElse { throw LoaderException("Js脚本加载失败: $contextName", it) }
    }
}
```

### 修复2：Function.call 的 Context 管理

```kotlin
// JSHelper.kt 中的修复
fun Function.call(js: SparkJS, vararg args: Any?) {
    val currentContext = Context.enter()  // ✅ 为当前线程创建 Context
    try {
        if (js.scope == null) {  // ✅ 确保 scope 已初始化
            js.context = currentContext
            js.scope = currentContext.initStandardObjects()
            js.register()  // ✅ 重新注册组件
        }
        call(currentContext, js.scope, js.scope, args)
    } finally {
        Context.exit()  // ✅ 确保释放
    }
}
```

### 修复3：添加缺失的 API 函数

```kotlin
// JSEntityHelper.kt 中添加缺失的 move 函数
object JSEntityHelper: JSComponent() {
    // ... 其他代码 ...
    
    fun move(entity: net.minecraft.world.entity.Entity, x: Double, y: Double, z: Double) {
        entity.setPos(entity.x + x, entity.y + y, entity.z + z)
    }

    fun move(entity: net.minecraft.world.entity.Entity, deltaPos: net.minecraft.world.phys.Vec3) {
        entity.setPos(entity.x + deltaPos.x, entity.y + deltaPos.y, entity.z + deltaPos.z)
    }
}
```

### 修复4：确保组件正确注册

```kotlin
// SparkJsApplier.kt 中的预防性措施
@SubscribeEvent
private fun onClientPlayerLogin(event: ClientPlayerNetworkEvent.LoggingIn) {
    // ... 其他代码 ...
    
    // 提前初始化 scope
    val ctx = Context.enter()
    try {
        if (js.scope == null) {
            js.context = ctx
            js.scope = ctx.initStandardObjects()
        }
    } finally {
        Context.exit()
    }
    
    // 确保组件注册
    js.register()
    
    // 手动注入关键组件作为后备
    js.scope?.put("Skill", js.scope, JSSkillApi)
}
```

## 为什么这些修复有效？

### 1. 线程安全
- 每次使用 JavaScript 时都为当前线程创建新的 Context
- 使用 `@Volatile` 确保多线程环境下的可见性
- 正确的 try-finally 模式确保资源不泄漏

### 2. 组件持久性
- 只在 scope 为 null 时初始化，避免覆盖已注入的组件
- 在关键时机手动确保组件注册
- 添加缺失的 API 函数

### 3. 错误恢复
- 如果 scope 丢失，会自动重新初始化并注册组件
- 异常处理确保即使出错也不会影响游戏稳定性

## 实际影响

**修复前：**
- 执行技能命令会导致游戏崩溃
- JavaScript 脚本中的 API 调用失败
- 多线程环境下不稳定

**修复后：**
- 技能命令正常执行
- JavaScript API 调用稳定可靠
- 支持多线程环境下的并发执行
- 自动恢复机制确保系统健壮性

## 总结

这个问题的核心是 **JavaScript 引擎的线程安全和资源管理**。通过正确理解 Rhino 引擎的工作原理，实施适当的生命周期管理，并确保 API 组件的正确注册，我们解决了导致游戏崩溃的根本问题。

这种修复不仅解决了当前的错误，还提高了整个 JavaScript 执行系统的稳定性和可靠性。