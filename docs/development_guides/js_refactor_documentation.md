# JavaScript 脚本加载和同步机制重构文档

## 概述

本次重构针对 SparkCore 的 JavaScript 脚本系统进行了全面的架构优化，主要目标是提升系统的稳定性、性能和可维护性。重构涉及攻击系统增强、初始化流程优化、同步机制改进以及物理碰撞处理的完善。

## 重构内容详解

### 1. 新增 AttackSystemMixin 类实现攻击系统功能

#### 1.1 核心变更

**新增文件：**
- `src/main/java/cn/solarmoon/spark_core/mixin/js/AttackSystemMixin.java`
- `src/main/kotlin/cn/solarmoon/spark_core/js/extension/JSAttackSystem.kt`

**实现细节：**
```java
@Mixin(AttackSystem.class)
public class AttackSystemMixin implements JSAttackSystem {
    // 通过 Mixin 将 JSAttackSystem 接口混入到 AttackSystem 类中
}
```

```kotlin
interface JSAttackSystem {
    val attackSystem get() = this as AttackSystem
    
    // 提供 JavaScript 友好的攻击系统API
    fun reset()
    fun hasAttacked(entityId: Int): Boolean
    fun setAutoReset(enabled: Boolean, resetAfterTicks: Int = 1)
    fun getTicksSinceLastReset(): Int
    fun getAttackedEntityCount(): Int
    fun setIgnoreInvulnerableTime(ignore: Boolean)
}
```

#### 1.2 功能增强

- **攻击状态管理**：提供了更完善的攻击状态跟踪和重置机制
- **JavaScript 集成**：将攻击系统暴露给 JavaScript 脚本，支持脚本化攻击逻辑
- **网络同步**：通过 `AttackSystemSyncPayload` 实现客户端与服务端的状态同步

### 2. 优化 SparkCore 初始化流程，确保 JSApi 正确初始化

#### 2.1 初始化顺序调整

**修改文件：** `src/main/java/cn/solarmoon/spark_core/SparkCore.java`

**关键变更：**
```java
// 显式初始化 JSApi.ALL，确保在 HandlerDiscoveryService 之前完成
JSApi.Companion.register();

HandlerDiscoveryService.INSTANCE.discoverAndInitializeHandlers(getClass());
```

#### 2.2 解决的问题

- **依赖顺序问题**：确保 JavaScript API 在资源处理器发现之前完成注册
- **初始化竞态**：避免因初始化顺序不当导致的 `UninitializedPropertyAccessException`
- **系统稳定性**：提高模组启动的可靠性

### 3. 修正 AnimInstance 中的视线锁定逻辑

#### 3.1 核心修改

**修改文件：** `src/main/kotlin/cn/solarmoon/spark_core/animation/anim/play/AnimInstance.kt`

**变更内容：**
```kotlin
// 锁定ai的视线
var shouldTurnHead = true  // 从 false 修改为 true
```

#### 3.2 影响分析

- **动画行为**：允许动画实例控制实体的头部转动
- **AI 行为**：修正了实体视线锁定的默认行为
- **视觉效果**：改善了动画播放时的视觉表现

**相关 Mixin：** `LookControlMixin.java` 会检查此标志决定是否取消头部转动

### 4. 改进 JSEntity 接口，增加日志输出功能

#### 4.1 接口增强

**修改文件：** `src/main/kotlin/cn/solarmoon/spark_core/js/extension/JSEntity.kt`

**新增功能：**
```kotlin
interface JSEntity {
    // 调试支持
    fun commonAttack(target: Entity, currentAttackPhase: Int) {
        // 增加了当前攻击阶段参数用于调试
        println("attack on currentAttackPhase $currentAttackPhase")
        // ... 攻击逻辑
    }
    
    // 新增日志输出方法
    fun log(message: String) {
        SparkCore.LOGGER.info("发送消息: {}", message)
    }
}
```

#### 4.2 功能价值

- **调试支持**：为 JavaScript 脚本提供直接的日志输出能力
- **攻击系统增强**：支持攻击阶段跟踪和调试
- **开发体验**：简化脚本调试流程

### 5. 重构 JS 物理碰撞对象的攻击回调逻辑

#### 5.1 回调机制优化

**核心文件：**
- `src/main/kotlin/cn/solarmoon/spark_core/physics/presets/callback/AttackCollisionCallback.kt`
- `src/main/kotlin/cn/solarmoon/spark_core/js/extension/JSPhysicsCollisionObject.kt`

**重构要点：**
```kotlin
interface AttackCollisionCallback: CollisionCallback {
    val attackSystem: AttackSystem
    
    override fun onProcessed(o1: PhysicsCollisionObject, o2: PhysicsCollisionObject, manifoldId: Long) {
        val attacker = o1.owner as? Entity ?: return
        (o2.owner as? Entity)?.apply {
            attackSystem.customAttack(this) {
                // 优化后的攻击逻辑
                preAttack(attackSystem.attackedEntities.isEmpty(), attacker, this@apply, o1, o2, manifoldId)
                if (!doAttack(attacker, this@apply, o1, o2, manifoldId)) return@customAttack false
                postAttack(attacker, this@apply, o1, o2, manifoldId)
                true
            }
        }
    }
}
```

#### 5.2 JavaScript 集成

```kotlin
fun onAttackCollide(id: String, consumer: Scriptable, customAttackSystem: AttackSystem? = null) {
    // 支持自定义攻击系统
    // 提供 preAttack, doAttack, postAttack 钩子
    // 线程安全的脚本执行
}
```

### 6. 优化 JS 脚本同步数据结构和传输方式

#### 6.1 新的同步架构

**核心组件：**
- `JSScriptDataSyncPayload` - 全量同步数据包
- `JSIncrementalSyncS2CPacket` - 增量同步数据包
- `JSScriptDataSendingTask` - 配置阶段任务

**数据结构优化：**
```kotlin
data class JSScriptDataSyncPayload(
    val scripts: LinkedHashMap<ResourceLocation, OJSScript>
) : CustomPacketPayload

data class JSIncrementalSyncS2CPacket(
    val apiId: String,
    val fileName: String,
    val operationType: ChangeType,
    val scriptContent: String
) : CustomPacketPayload
```

#### 6.2 同步策略改进

- **全量同步**：新玩家加入时发送完整脚本数据
- **增量同步**：运行时脚本变更的实时同步
- **容错机制**：客户端确认机制确保同步完整性
- **性能优化**：减少不必要的数据传输

### 7. 重新设计客户端和服务端 JS 引擎执行策略

#### 7.1 线程安全执行

**服务端策略** (`ServerSparkJS.kt`)：
```kotlin
override fun executeScript(script: OJSScript) {
    val server = ServerLifecycleHooks.getCurrentServer()
    if (server != null) {
        // 确保在服务器主线程执行
        server.execute {
            super.executeScript(script)
        }
    } else {
        // 启动阶段直接执行
        super.executeScript(script)
    }
}
```

**客户端策略** (`ClientSparkJS.kt`)：
```kotlin
override fun executeScript(script: OJSScript) {
    val minecraft = Minecraft.getInstance()
    if (minecraft.isSameThread) {
        super.executeScript(script)
    } else {
        // 切换到渲染线程执行
        minecraft.execute {
            super.executeScript(script)
        }
    }
}
```

#### 7.2 执行时机优化

- **服务端**：在 Level 加载时立即从注册表加载脚本
- **客户端**：延迟到玩家登录完成后加载，避免网络同步冲突
- **热重载**：支持运行时脚本重载，同时保证线程安全

### 8. 统一 JS 脚本注册、加载和卸载接口

#### 8.1 统一接口设计

**基类方法** (`SparkJS.kt`)：
```kotlin
abstract class SparkJS {
    // 从动态注册表加载所有脚本
    open fun loadAllFromRegistry()
    
    // 重新加载单个脚本
    open fun reloadScript(script: OJSScript)
    
    // 卸载脚本
    open fun unloadScript(apiId: String, fileName: String)
    
    // 线程安全的脚本执行（子类重写）
    protected open fun executeScript(script: OJSScript)
}
```

#### 8.2 应用层简化

**初始化统一**：
```kotlin
// SparkJsApplier.kt
if (js is ServerSparkJS) {
    js.loadAllFromRegistry()  // 服务端立即加载
} else {
    // 客户端延迟加载
}
```


#### 9.2 性能提升

- **碰撞检测缓存**：避免重复计算
- **动态添加地形**：按需加载周围地形块
- **碰撞体管理**：改进生命周期和内存使用

### 10. 调整动态注册表和动画处理器的内部实现

#### 10.1 注册表优化

**核心改进：**
- **统一数据访问**：`SparkRegistries.JS_SCRIPTS.getDynamicEntries()`
- **同步机制**：自动触发网络同步的回调机制
- **容错处理**：改善错误处理和恢复机制

**关键代码：**
```kotlin
val JS_SCRIPTS = (SparkCore.REGISTER.registry<OJSScript>()
    .id("js_scripts")
    .valueType(OJSScript::class)
    .build { it.sync(true).create() } as? DynamicAwareRegistry<OJSScript>)
    ?.apply {
        this.onDynamicRegister = { key, value ->
            // 自动同步到客户端
            val packet = DynamicRegistrySyncS2CPacket.createForJSScriptAdd(key.location(), value)
            PacketDistributor.sendToAllPlayers(packet)
        }
    }
```

#### 10.2 动画处理器增强

- **TypedAnimation 注册**：改进动态动画的注册和管理
- **资源热重载**：支持动画资源的运行时更新
- **同步优化**：减少动画同步的网络开销

## 技术亮点

### 1. 线程安全设计

- **服务端**：所有脚本执行都在主线程进行
- **客户端**：确保在渲染线程执行，避免线程冲突
- **资源处理**：使用任务队列处理文件系统变更

### 2. 网络同步优化

- **配置阶段同步**：新玩家加入时的全量数据传输
- **增量同步**：运行时的最小化数据传输
- **确认机制**：确保数据传输的可靠性

### 3. 错误处理增强

- **防御性编程**：添加大量空值检查和异常处理
- **优雅降级**：当某些组件不可用时的回退机制
- **调试支持**：详细的日志输出和错误追踪

### 4. 性能优化

- **懒加载**：按需加载和初始化组件
- **缓存机制**：减少重复计算和网络传输
- **内存管理**：改进对象生命周期管理

## 向后兼容性

### 保持兼容的设计

- **API 稳定性**：现有 JavaScript API 保持不变
- **配置兼容**：支持旧的配置文件格式
- **渐进迁移**：新旧机制并存，允许逐步迁移

### 迁移指南

开发者无需修改现有的 JavaScript 脚本，新的机制在后台透明运行。但建议：

1. **日志调试**：利用新的 `entity.log()` 方法替代 `print()`
2. **攻击系统**：使用新的攻击系统 API 获得更好的性能
3. **错误处理**：添加适当的错误处理逻辑

## 测试验证

### 验证要点

1. **脚本加载**：确认所有脚本正确加载和执行
2. **网络同步**：验证客户端与服务端的数据一致性
3. **热重载**：测试运行时脚本修改的即时生效
4. **攻击系统**：验证攻击逻辑的正确性和性能
5. **线程安全**：确认无并发问题和竞态条件

### 性能指标

- **启动时间**：模组初始化时间优化
- **内存使用**：降低运行时内存占用
- **网络带宽**：减少同步数据传输量
- **脚本执行**：提升 JavaScript 脚本执行效率

## 结论

本次重构显著提升了 SparkCore JavaScript 系统的稳定性、性能和可维护性。通过统一的接口设计、优化的同步机制和增强的错误处理，为开发者提供了更加可靠和高效的脚本开发环境。同时，新的攻击系统和物理碰撞处理为游戏机制的实现提供了更强大的支持。 