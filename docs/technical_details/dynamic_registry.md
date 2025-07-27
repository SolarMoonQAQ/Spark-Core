# 动态注册表 (Dynamic Registry) 技术文档

## 1. 概述

### 1.1 什么是动态注册表？

动态注册表 (`DynamicAwareRegistry`) 是 SparkCore 资源系统的核心基石。它扩展了 Minecraft/NeoForge 的原生注册表，允许在游戏**运行时**动态地添加和移除资源条目。这个功能对于实现资源热重载、模块化内容和脚本化API至关重要。

几乎所有由 `ResourceHandler` (如 `AnimationHandler`, `ModelHandler` 等) 管理的动态资源，最终都会被注册到相应的动态注册表中，从而在游戏中生效。

### 1.2 支持的注册表类型

SparkCore 中所有通过 `SparkRegistries` 暴露的注册表均为动态注册表，包括：
- `TypedAnimation`
- `OModel`
- `OJSScript`
- `OTexture`
- `TypedIKComponent`

## 2. 架构与实现原理

### 2.1 设计模式

`DynamicAwareRegistry` 采用**装饰器模式（Decorator Pattern）**实现。它包装了一个静态的 `Registry<T>` 实例，在不改变其核心接口的前提下，增加了动态管理条目的能力。

为了确保每个静态注册表只有一个动态包装器，系统采用了一个单例工厂模式，保证了对同一个注册表的所有引用都指向同一个 `DynamicAwareRegistry` 实例，维持了数据一致性。

### 2.2 核心数据结构

```kotlin
class DynamicAwareRegistry<T>(private val staticRegistry: Registry<T>) : Registry<T> {
    // 存储动态注册的条目
    private val dynamicEntries = ConcurrentHashMap<ResourceLocation, T>()
    
    // 线程安全锁
    private val lock: ReadWriteLock = ReentrantReadWriteLock()

    // 动态ID分配器
    private val nextDynamicId = AtomicInteger(findMaxStaticId() + 1)
    
    // 网络同步回调
    var onDynamicRegister: ((key: ResourceKey<T>, value: T) -> Unit)? = null
    var onDynamicUnregister: ((key: ResourceKey<T>, value: T) -> Unit)? = null
    
    // ... 实现了 Registry<T> 的所有接口方法
}
```

### 2.3 线程安全

动态注册表被设计为完全线程安全的，以应对来自文件监控和网络事件的并发访问。
- **读写锁**: 使用 `ReadWriteLock` 保护对 `dynamicEntries` 的并发访问。读操作（`get`, `containsKey`）使用共享的读锁，而写操作（`registerDynamic`, `unregisterDynamic`）使用排他的写锁。
- **并发集合**: 内部使用 `ConcurrentHashMap` 等并发安全的数据结构。
- **原子操作**: 使用 `AtomicInteger` 来安全地生成唯一的动态ID。

### 2.4 数据访问优先级

当查询一个资源时，`DynamicAwareRegistry` 的 `get()` 方法会遵循以下顺序：
1.  **首先，检查动态条目**: 使用读锁安全地查询 `dynamicEntries`。如果找到，立即返回。
2.  **然后，查询静态条目**: 如果动态条目中不存在，则委托给被包装的原始静态注册表进行查询。

这种设计使得动态资源可以无缝地“覆盖”同名的静态资源。

## 3. 使用方法与集成

在新的资源架构下，开发者通常**不会直接调用**`registerDynamic`或`unregisterDynamic`。这些操作由具体的`ResourceHandler`在其内部逻辑中完成。

### 3.1 资源处理器中的集成

以 `AnimationHandler` 为例，其处理新资源的流程如下：

```kotlin
// AnimationHandler.kt
override fun processResourceAdded(node: ResourceNode) {
    // 1. 解析动画文件，创建 OAnimationSet 对象
    val animationSet = parseAnimationFile(node)
    
    // 2. 存入静态缓存
    OAnimationSet.ORIGINS[node.id] = animationSet
    
    // 3. 注册到动态注册表
    registerToRegistry(node.id, animationSet) 
}

private fun registerToRegistry(location: ResourceLocation, animationSet: OAnimationSet) {
    // ... 创建 TypedAnimation ...
    val resourceKey = ResourceKey.create(typedAnimationRegistry.key(), location)
    
    // 这里调用的是注册表的标准 register 方法，
    // DynamicAwareRegistry 会处理后续逻辑
    typedAnimationRegistry.register(resourceKey, typedAnimation, RegistrationInfo.BUILT_IN)
}
```

### 3.2 动态操作接口

尽管不常用，但 `DynamicAwareRegistry` 依然提供了直接的动态操作接口，主要用于调试或特殊的管理场景。

- **`registerDynamic(id: ResourceLocation, value: T, moduleId: String)`**: 注册一个动态条目。
- **`unregisterDynamic(id: ResourceLocation)`**: 注销一个动态条目。
- **`clearDynamic()`**: 清除所有动态条目。
- **`getDynamicEntries()`**: 获取所有动态注册的条目。
- **`isDynamic(id: ResourceLocation)`**: 检查一个条目是否为动态注册。

## 4. 网络同步

动态注册表的网络同步是自动化的，通过回调机制实现。

### 4.1 自动同步回调

在 `SparkRegistries.kt` 中创建注册表时，会为其绑定同步回调：

```kotlin
// SparkRegistries.kt
val TYPED_ANIMATION = (SparkCore.REGISTER.registry<TypedAnimation>()
    .build { ... } as? DynamicAwareRegistry<TypedAnimation>)
    ?.apply {
        // 当一个条目被动态注册时，这个回调会被触发
        this.onDynamicRegister = { key, value ->
            val packet = DynamicRegistrySyncS2CPacket.createForTypedAnimationAdd(key.location(), value)
            PacketDistributor.sendToAllPlayers(packet)
        }
        // 同样也为 onDynamicUnregister 设置回调
        this.onDynamicUnregister = { key, _ ->
            DynamicRegistrySyncS2CPacket.syncTypedAnimationRemovalToClients(key.location())
        }
    }
```

### 4.2 同步流程

1.  一个 `ResourceHandler` 调用 `registerDynamic` 或 `unregisterDynamic`。
2.  `DynamicAwareRegistry` 内部完成注册/注销操作。
3.  对应的 `onDynamicRegister` 或 `onDynamicUnregister` 回调被触发。
4.  回调函数创建一个增量同步包 `DynamicRegistrySyncS2CPacket`。
5.  数据包被广播到所有客户端。
6.  客户端接收到数据包，并调用本地的 `DynamicAwareRegistry` 来应用相同的变更，从而保持同步。

## 5. 注意事项与最佳实践

- **性能**: 虽然动态注册表是线程安全的，但大量的写操作（注册/注销）仍然可能在高并发场景下引入性能开销。对于不常变动的内容，应优先使用静态注册。
- **数据持久化**: 动态注册的条目是**非持久化**的，它们存储在内存中。游戏重启后，所有动态资源都需要通过资源处理器重新加载和注册。
- **Holder 系统**: 与 Minecraft 原版的 Holder 系统（`Holder<T>`）的交互可能存在限制。依赖于 Holder 的高级功能可能无法完全支持动态注册的条目。
- **调试**: 使用 `/spark deps stats` 等命令可以查看动态资源的加载情况。在调试时，可以利用 `getDynamicEntries()` 和 `isDynamic()` 来检查注册表状态。
