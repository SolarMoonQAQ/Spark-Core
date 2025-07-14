# SparkCore 调试指南

## 1. 概述

本指南提供了 SparkCore 系统的全面调试方法，包括日志系统、性能监控、错误诊断和问题解决策略。掌握这些调试技巧将帮助您快速定位和解决开发中遇到的问题。

### 1.1 调试环境准备

在 `config/sparkcore-common.toml` (或类似配置文件) 中启用调试选项：

```toml
# 启用或禁用全局调试模式
debug_mode = true

# 设置日志级别 (TRACE, DEBUG, INFO, WARN, ERROR)
log_level = "DEBUG"

# 启用性能监控
performance_monitoring = true
```

## 2. 日志系统调试

### 2.1 日志级别和用途

- **`ERROR`**: 系统级错误，通常伴随异常，导致功能中断。
  - `SparkCore.LOGGER.error("资源加载失败: {}", resourcePath, exception)`
- **`WARN`**: 潜在问题或不建议的操作，系统可恢复。
  - `SparkCore.LOGGER.warn("检测到循环依赖: {} -> {}", resourceA, resourceB)`
- **`INFO`**: 重要的系统状态变更和生命周期事件。
  - `SparkCore.LOGGER.info("ResourceGraphManager 已初始化，发现 {} 个节点", count)`
- **`DEBUG`**: 用于开发和调试的详细执行流程。
  - `SparkCore.LOGGER.debug("正在处理资源: {}, 类型: {}", node.id, node.resourceType)`
- **`TRACE`**: 最详细的信息，用于深度追踪方法调用和变量状态。
  - `SparkCore.LOGGER.trace("方法调用: {}#{}", className, methodName)`

### 2.2 系统组件日志

**资源图系统日志**:
```kotlin
// ResourceGraphManager & Helpers
SparkCore.LOGGER.info("ResourceGraphManager 开始初始化...")
SparkCore.LOGGER.debug("解析路径: {} -> {}", filePath, resourceLocation)
SparkCore.LOGGER.debug("加载元数据 for {}", resourceLocation)
SparkCore.LOGGER.debug("计算依赖 for {}", resourceLocation)
SparkCore.LOGGER.warn("依赖验证失败: {} -> {}", resourceId, missingDependency)
SparkCore.LOGGER.error("发现循环依赖", exception)

// Resource Handlers
SparkCore.LOGGER.info("AnimationHandler 初始化完成，处理了 {} 个动画", count)
SparkCore.LOGGER.debug("资源已添加: {}", node.id)
```

**JavaScript 系统日志**:
```kotlin
// JavaScript 引擎
SparkCore.LOGGER.info("JavaScript 引擎初始化完成")
SparkCore.LOGGER.debug("加载脚本: {} (API: {})", script.fileName, script.apiId)
SparkCore.LOGGER.error("脚本执行错误: {}", script.location, exception)

// 脚本同步
SparkCore.LOGGER.info("脚本全量同步完成，发送 {} 个脚本到客户端", scriptCount)
SparkCore.LOGGER.debug("脚本增量热重载: {}", script.location)
```

**网络同步日志**:
```kotlin
// 数据包发送
SparkCore.LOGGER.info("发送同步包: {} to player {}", packet.type.id, player.name.string)
SparkCore.LOGGER.debug("数据包大小: {} bytes", packet.size)

// 同步状态
SparkCore.LOGGER.warn("网络同步失败，重试中...", error)
```

## 3. 游戏内调试命令

新资源系统提供了一套强大的命令行工具来实时检查和调试系统状态。

### 3.1 资源依赖命令 (`/spark deps ...`)

- **`/spark deps stats`**
  - **功能**: 显示资源图和模块图的总体统计信息，如节点总数、边的数量、已加载的模块等。
  - **用途**: 快速了解整个资源系统的健康状况。

- **`/spark deps check <resource_id>`**
  - **功能**: 验证指定资源的所有依赖关系是否满足。
  - **参数**: `resource_id` (e.g., `spark_core:sparkcore/animations/player/walk_animation`)
  - **用途**: 诊断特定资源加载失败或行为异常的原因。
  - **示例输出**:
    ```
    === 检查资源依赖: my_mod:my_mod/animations/player/combat/sword_attack ===
    ✓ HARD: spark_core:sparkcore/models/player
    ⚠ SOFT: spark_core:sparkcore/effects/sword_slash (资源不存在)
    ```

- **`/spark deps tree <resource_id>`**
  - **功能**: 以树状结构显示指定资源的直接和间接依赖。
  - **用途**: 可视化一个资源的完整依赖链，快速发现深层次的依赖问题。

- **`/spark deps dependents <resource_id>`**
  - **功能**: 显示哪些其他资源**依赖于**指定的资源。
  - **用途**: 在修改或删除一个核心资源前，评估其影响范围。

- **`/spark deps orphans [modId]`**
  - **功能**: 查找指定模组下没有任何其他资源依赖的“孤立”资源。
  - **用途**: 清理项目中不再使用的无用资源。
  - **示例**: `/spark deps orphans my_mod` 查找my_mod模组下的孤立资源

### 3.2 JavaScript 调试命令

- **`/spark script list [api_id]`**
  - **功能**: 列出所有已加载的脚本，可选按API模块过滤。
  - **用途**: 检查脚本是否被系统正确发现和加载。

- **`/spark script reload <resource_id>`**
  - **功能**: 强制从文件系统重新加载并执行指定的JS脚本。
  - **用途**: 在不修改文件时间戳的情况下，手动触发单个脚本的热重载。

- **`/spark script eval <javascript_code>`**
  - **功能**: 在游戏内直接执行一小段JS代码。
  - **用途**: 快速测试API调用或检查运行时变量。

## 4. 性能监控和分析

### 4.1 性能监控工具

**系统性能监控**:
```kotlin
object PerformanceMonitor {
    // 使用 ConcurrentHashMap 存储计时器和计数器
    
    fun startTimer(name: String) { ... }
    fun endTimer(name: String): Long { ... }
    fun incrementCounter(name: String) { ... }
    
    fun getStats(): Map<String, Any> { ... }
}

// 使用示例:
PerformanceMonitor.startTimer("ResourceGraphBuild")
// ... 执行操作 ...
PerformanceMonitor.endTimer("ResourceGraphBuild")
```

**JavaScript 性能监控**:
```javascript
// 全局性能分析器
const profiler = new PerformanceProfiler();

// 使用示例
profiler.start("myComplexFunction");
// ... 执行代码 ...
profiler.end("myComplexFunction");
// 输出: Performance [myComplexFunction]: 123ms
```

### 4.2 内存监控

```kotlin
object MemoryMonitor {
    fun logMemoryUsage(context: String) {
        // ... 获取并打印JVM内存使用情况 ...
    }
}
// 在关键节点调用，如资源全部加载后
MemoryMonitor.logMemoryUsage("After full resource load")
```

## 5. 错误诊断和调试

### 5.1 常见错误类型

- **资源加载错误**:
  - `ResourceParseException`: 文件格式错误（如JSON语法错误）。
  - `DependencyValidationException`: 依赖验证失败（如硬依赖缺失）。
  - `ModuleOperationException`: 模块操作失败（如循环依赖）。
- **JavaScript 执行错误**:
  - `EvaluatorException`: 脚本语法或运行时错误。
- **网络同步错误**:
  - `CodecException`: 数据包序列化或反序列化失败。

### 5.2 错误堆栈分析

当出现异常时，仔细阅读堆栈信息：
- **顶层异常**: 通常指明了问题的直接原因。
- **根本原因 (Caused by)**: 揭示了问题的根源。
- **SparkCore相关堆栈**: 关注`cn.solarmoon.spark_core`包下的调用，它们是定位问题的关键。

### 5.3 断点调试技巧

- **条件断点**: 在IDE中设置断点，当满足特定条件时（如处理某个特定资源`resource.id == "problematic_resource"`）暂停执行。
- **日志断点**: 在不暂停程序的情况下，打印出特定位置的变量信息或日志。
- **异常断点**: 设置在特定异常（如 `DependencyValidationException`）抛出时暂停，直接定位到问题发生的源头。

## 6. 四层目录结构调试指导

### 6.1 四层目录结构常见问题

**问题1: 资源未被发现**
- **症状**: 资源文件存在但系统无法加载
- **原因**: 目录结构不符合四层格式
- **解决方案**:
  ```
  错误: run/my_mod/animations/player.json
  正确: run/sparkcore/my_mod/my_mod/animations/player.json
  ```

**问题2: ResourceLocation格式错误**
- **症状**: 依赖引用失败，找不到资源
- **原因**: 使用旧的ResourceLocation格式
- **解决方案**:
  ```
  错误: "my_mod:player"
  正确: "my_mod:my_mod/animations/player"
  ```

**问题3: 模块标识混淆**
- **症状**: 跨模块依赖检测失败
- **原因**: 模块标识格式不正确
- **解决方案**: 使用完整模块标识`{modId}:{moduleName}`
  ```
  示例: "my_mod:my_mod", "my_mod:combat_system", "spark_core:sparkcore"
  ```

### 6.2 四层结构调试命令

**检查目录结构**:
```bash
# 检查四层目录是否正确
/spark deps stats  # 查看发现的模块数量
/spark deps check my_mod:my_mod/animations/player  # 检查具体资源
```

**模块标识调试**:
```bash
# 列出所有模块
/spark modules list
# 检查模块依赖
/spark modules deps my_mod:my_mod
```

**路径解析调试**:
```bash
# 检查路径解析结果
/spark debug path run/sparkcore/my_mod/my_mod/animations/player.json
# 预期输出: ResourceLocation: my_mod:my_mod/animations/player
```

### 6.3 热重载调试

**四层结构热重载**:
- 监控目录: `run/sparkcore/{modId}/{moduleName}/`
- 支持的操作: 文件创建、修改、删除
- 调试命令: `/spark hotreload status` 查看监控状态

**常见热重载问题**:
1. **文件监控失效**: 检查目录权限和路径格式
2. **部分更新失败**: 使用`/spark hotreload force {resourceId}`强制重载
3. **依赖更新延迟**: 依赖资源会自动重载，等待几秒钟

### 6.4 JavaScript脚本路径调试

**脚本路径格式**:
```
物理路径: run/sparkcore/my_mod/my_mod/scripts/skill/fireball.js
ResourceID: my_mod:my_mod/scripts/skill/fireball
API ID: skill (从scripts/后提取)
```

**脚本路径调试命令**:
```bash
# 检查脚本是否被发现
/spark script list skill
# 检查API ID提取
/spark debug script-path my_mod:my_mod/scripts/skill/fireball
```

## 7. 问题解决策略

### 7.1 资源加载问题排查流程

1.  **检查路径和命名**: 确认资源文件是否在正确的`run/sparkcore/{modId}/{moduleName}/{resourceType}/`四层目录下，且文件名符合规范。
2.  **验证文件内容**: 确保JSON、GLSL等文件没有语法错误。可以使用在线校验工具。
3.  **使用调试命令**:
    -   运行 `/spark deps check <resource_id>` 检查依赖。
    -   如果依赖缺失，继续对缺失的依赖运行 `check`，直到找到根源。
4.  **检查日志**: 查找与该资源相关的`ERROR`或`WARN`日志。`DEBUG`级别的日志可以提供更详细的加载步骤。
5.  **检查元数据**: 打开`.meta.json`文件，确认`requires`中的资源ID是否正确无误。

### 7.2 JavaScript 问题调试流程

1.  **检查加载**: 运行 `/spark script list` 确认脚本是否被加载。
2.  **简化问题**: 如果脚本复杂，尝试注释掉部分代码，逐步排查问题范围。
3.  **使用`Logger`**: 在脚本关键位置添加 `Logger.info(...)` 或 `Logger.debug(...)` 打印变量或执行流程。
4.  **使用`eval`命令**: 使用 `/spark script eval "..."` 测试特定的API调用是否正常。
5.  **检查依赖**: 确保脚本的`.meta.json`中声明了所有必要的资源依赖（如动画、模型）。

## 8. 总结

SparkCore的新调试工具和增强的日志系统，结合四层目录结构的设计，为开发者提供了强大的问题定位能力。通过结合使用游戏内命令、详细日志和性能监控，开发者可以高效地诊断和解决从资源加载到脚本执行的各类问题。

**四层目录结构调试优势**:
- **精确定位**: 通过完整的模块标识`{modId}:{moduleName}`快速定位问题模块
- **路径清晰**: 四层路径格式使资源组织更加清晰，便于问题排查
- **模块隔离**: 不同模块的问题相互独立，降低调试复杂度
- **向后兼容**: 支持旧格式资源的调试，平滑迁移过程

**调试核心思想**:
1.  **观察 (Observe)**: 通过日志和命令了解系统状态。
2.  **假设 (Hypothesize)**: 根据观察到的现象提出可能的原因。
3.  **验证 (Verify)**: 使用更精确的调试手段（如断点、更详细的日志）来验证假设。
4.  **修复 (Fix)**: 解决问题并回归测试。