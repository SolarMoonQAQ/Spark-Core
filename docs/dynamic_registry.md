# 动态注册表 (Dynamic Registry) 使用指南

本文档介绍了 Spark Core 中的动态注册表功能，它允许在游戏运行时动态注册和卸载条目，而不需要在游戏启动时通过 DeferredRegister 进行静态注册。

> [!NOTE]
> 本文档既包含使用指南，也包含实现细节，适合开发者阅读。

## 1. 概述

### 1.1 什么是动态注册表？

动态注册表 (DynamicAwareRegistry) 是对 Minecraft/NeoForge 原生注册表的扩展，它允许在游戏运行时动态添加和移除条目。这对于以下场景特别有用：

- 模组运行时加载资源和行为
- 服务器插件系统
- 脚本 API 扩展
- 热重载功能

### 1.2 支持的注册表类型

目前，以下注册表类型支持动态注册：

- `TypedAnimation`：类型化动画
- 未来会支持更多类型

## 2. 使用方法

### 2.1 获取动态注册表

所有通过 `SparkCore.REGISTER.registry()` 创建的注册表都已经是 DynamicAwareRegistry 类型，可以直接使用动态注册功能：

```kotlin
// 获取 TypedAnimation 注册表
val registry = SparkRegistries.TYPED_ANIMATION

// 检查是否为动态注册表
if (registry is DynamicAwareRegistry<*>) {
    // 使用动态注册功能
}
```

### 2.2 动态注册条目

有两种方式可以动态注册条目：

#### 2.2.1 使用 DynamicAwareRegistry 直接注册

```kotlin
// 创建要注册的条目
val animIndex = AnimIndex(ResourceLocation.fromNamespaceAndPath("mymod", "model"), "custom_animation")
val animation = TypedAnimation(animIndex) {
    // 动画行为配置
}

// 注册到动态注册表
val registry = SparkRegistries.TYPED_ANIMATION as DynamicAwareRegistry<TypedAnimation>
val id = ResourceLocation.fromNamespaceAndPath("mymod", "custom_animation")
registry.registerDynamic(id, animation)
```

#### 2.2.2 使用 TypedAnimationBuilder 的扩展方法

```kotlin
// 使用 Builder 模式创建并注册
val animation = SparkCore.REGISTER.typedAnimation()
    .id(ResourceLocation.fromNamespaceAndPath("mymod", "custom_animation"))
    .animIndex(AnimIndex(ResourceLocation.fromNamespaceAndPath("mymod", "model"), "custom_animation"))
    .provider {
        // 动画行为配置
    }
    .registerDynamic(ResourceLocation.fromNamespaceAndPath("mymod", "custom_animation"))
```

### 2.3 取消注册条目

```kotlin
val registry = SparkRegistries.TYPED_ANIMATION as DynamicAwareRegistry<TypedAnimation>
val id = ResourceLocation.fromNamespaceAndPath("mymod", "custom_animation")
registry.unregisterDynamic(id)
```

### 2.4 清除所有动态条目

```kotlin
val registry = SparkRegistries.TYPED_ANIMATION as DynamicAwareRegistry<TypedAnimation>
registry.clearDynamic()
```

### 2.5 查询动态条目

```kotlin
val registry = SparkRegistries.TYPED_ANIMATION as DynamicAwareRegistry<TypedAnimation>

// 获取所有动态条目
val allDynamicEntries = registry.getDynamicEntries()

// 检查条目是否为动态注册的
val isDynamic = registry.isDynamic(ResourceLocation.fromNamespaceAndPath("mymod", "custom_animation"))
```

## 3. 网络同步

动态注册的条目需要在服务器和客户端之间同步。Spark Core 提供了自动同步机制。

### 3.1 服务器到客户端同步

当服务器动态注册一个条目后，可以使用 NetworkHandler 将其同步到所有客户端：

```kotlin
// 注册动画
val id = ResourceLocation.fromNamespaceAndPath("mymod", "custom_animation")
val animation = /* 创建动画 */
val registry = SparkRegistries.TYPED_ANIMATION as DynamicAwareRegistry<TypedAnimation>
registry.registerDynamic(id, animation)

// 同步到所有客户端
NetworkHandler.syncTypedAnimationToClients(id, animation)
```

### 3.2 客户端接收同步

客户端会自动接收并处理同步数据包，无需额外代码。

## 4. 实现细节与注意事项

### 4.1 DynamicAwareRegistry 实现原理

DynamicAwareRegistry 采用装饰器模式（Decorator Pattern）实现，它包装了静态的 Registry<T> 并添加了动态注册功能：

```kotlin
class DynamicAwareRegistry<T>(private val staticRegistry: Registry<T>) : Registry<T> {
    // 动态条目存储
    private val dynamicEntries = ConcurrentHashMap<ResourceLocation, T>()
    private val dynamicKeyToId = ConcurrentHashMap<ResourceLocation, Int>()
    private val dynamicIdToKey = ConcurrentHashMap<Int, ResourceLocation>()
    private val dynamicValueToKey = ConcurrentHashMap<T, ResourceLocation>()

    // 线程安全锁
    private val lock: ReadWriteLock = ReentrantReadWriteLock()

    // 动态ID生成器
    private val nextDynamicId = AtomicInteger(findMaxStaticId() + 1)

    // 实现 Registry<T> 接口的方法...
}
```

#### 4.1.1 单例包装机制

为了确保每个静态注册表只被包装一次，RegistryBuilder 使用了静态映射缓存：

```kotlin
companion object {
    private val staticRegistryMap = mutableMapOf<ResourceKey<*>, DynamicAwareRegistry<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <T> getOrCreateDynamicRegistry(staticRegistry: Registry<T>): DynamicAwareRegistry<T> {
        val key = staticRegistry.key()
        return staticRegistryMap.getOrPut(key) {
            DynamicAwareRegistry(staticRegistry)
        } as DynamicAwareRegistry<T>
    }
}
```

这确保了对同一个静态注册表的所有引用都使用同一个 DynamicAwareRegistry 实例，保持了系统的一致性。

### 4.2 线程安全

DynamicAwareRegistry 实现了线程安全的读写操作，可以在多线程环境中安全使用：

- 使用 `ReadWriteLock` 保护并发读写操作
- 使用 `ConcurrentHashMap` 存储动态条目
- 使用 `AtomicInteger` 生成唯一的动态 ID

```kotlin
// 读操作示例
override fun get(name: ResourceLocation?): T? {
    if (name == null) return null

    // 先查找动态条目
    lock.readLock().lock()
    try {
        val dynamicValue = dynamicEntries[name]
        if (dynamicValue != null) {
            return dynamicValue
        }
    } finally {
        lock.readLock().unlock()
    }

    // 再查找静态条目
    return staticRegistry.get(name)
}

// 写操作示例
fun registerDynamic(key: ResourceLocation, value: T): T {
    lock.writeLock().lock()
    try {
        // 检查并注册...
        val id = nextDynamicId.getAndIncrement()
        dynamicEntries[key] = value
        dynamicKeyToId[key] = id
        dynamicIdToKey[id] = key
        dynamicValueToKey[value] = key

        return value
    } finally {
        lock.writeLock().unlock()
    }
}
```

### 4.3 性能考虑

- 动态注册表在查询时会先检查动态条目，再检查静态条目，可能比纯静态注册表略慢
- 大量动态注册和取消注册操作可能影响性能
- 建议在必要时才使用动态注册，对于固定的内容仍推荐使用静态注册

### 4.4 网络同步实现

动态注册表同步使用自定义数据包实现：

```kotlin
class DynamicRegistrySyncS2CPacket(
    val registryKey: ResourceKey<*>,
    val entryId: ResourceLocation,
    val entryData: ByteArray
) : CustomPacketPayload {
    // ...
}
```

服务端注册动态条目后，可以发送同步数据包到所有客户端：

```kotlin
fun syncTypedAnimationToClients(id: ResourceLocation, animation: TypedAnimation) {
    val packet = DynamicRegistrySyncS2CPacket.createForTypedAnimation(id, animation)
    PacketDistributor.sendToAllPlayers(packet)
}
```

### 4.5 限制

- 动态注册的条目不会被保存到世界数据中，游戏重启后需要重新注册
- 某些与注册表深度集成的 Minecraft 功能可能不完全支持动态注册的条目
- Holder 系统的支持有限，可能影响某些高级功能

## 5. 错误码和故障排除

| 错误码 | 描述 | 解决方案 |
|--------|------|----------|
| `ALREADY_EXISTS_STATIC` | 尝试动态注册的条目 ID 已存在于静态注册表中 | 使用不同的 ID |
| `ALREADY_EXISTS_DYNAMIC` | 尝试动态注册的条目 ID 已存在于动态注册表中 | 先取消注册或使用不同的 ID |
| `NOT_DYNAMIC_REGISTRY` | 注册表不是 DynamicAwareRegistry 类型 | 确保使用 SparkCore.REGISTER.registry() 创建的注册表 |
| `SYNC_FAILED` | 网络同步失败 | 检查网络连接和数据包大小 |

## 6. 示例代码

### 6.1 从 JavaScript 动态注册 TypedAnimation

```javascript
// 获取注册表
var registry = Java.type("cn.solarmoon.spark_core.registry.common.SparkRegistries").TYPED_ANIMATION;

// 创建动画索引
var ResourceLocation = Java.type("net.minecraft.resources.ResourceLocation");
var AnimIndex = Java.type("cn.solarmoon.spark_core.animation.anim.origin.AnimIndex");
var modelLoc = ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "item_animatable");
var animIndex = new AnimIndex(modelLoc, "custom_js_animation");

// 创建动画
var TypedAnimation = Java.type("cn.solarmoon.spark_core.animation.anim.play.TypedAnimation");
var animation = new TypedAnimation(animIndex, function(instance) {
    // 动画行为配置
});

// 动态注册
var DynamicAwareRegistry = Java.type("cn.solarmoon.spark_core.registry.dynamic.DynamicAwareRegistry");
if (registry instanceof DynamicAwareRegistry) {
    var id = ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "item_animatable")
    registry.registerDynamic(id, animation);

    // 同步到客户端
    var NetworkHandler = Java.type("cn.solarmoon.spark_core.network.dynamic.NetworkHandler");
    NetworkHandler.INSTANCE.syncTypedAnimationToClients(id, animation);
}
```

### 6.2 使用 TypedAnimationBuilder 动态注册

```kotlin
// 创建并动态注册动画
val animation = SparkCore.REGISTER.typedAnimation()
    .id("custom_animation") // 只需提供路径部分
    .animIndex(AnimIndex(ResourceLocation.fromNamespaceAndPath("mymod", "model"), "custom_animation"))
    .provider {
        // 配置动画行为
        onStart {
            // 动画开始时的行为
        }
        onEnd {
            // 动画结束时的行为
        }
    }
    .registerDynamic(ResourceLocation.fromNamespaceAndPath("mymod", "custom_animation"))

// 使用动画
entity.animController.setAnimation(animation.create(entity), 5)
```
