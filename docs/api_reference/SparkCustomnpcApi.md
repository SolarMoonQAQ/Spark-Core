# SparkCustomnpcApi 文档

## 概述

`SparkCustomnpcApi` 是 Spark Core 为 CustomNPCs 提供的原生 Java API。它允许开发者通过 Java 代码直接调用 Spark Core 的核心功能，如动画控制、模型切换和碰撞事件处理等，而无需依赖 WebSocket 或 JS-bridge。

## 快速开始

### 初始化

```java
SparkCustomnpcApi api = new SparkCustomnpcApi();
```

## API 参考

### 模型控制

#### `changeModel(String modelId, String newModelPath)`

更改实体的模型。

**参数：**
- `modelId` - 实体的唯一标识符（可以是实体ID、UUID、自定义名称或标签）
- `newModelPath` - 新模型的资源路径，格式为 "namespace:path"

**示例：**
```java
api.changeModel("npc123", "minecraft:zombie");
```

### 动画控制

#### `playTypedAnimation(String UUID, String animationId, int transTime)`

播放已注册的 TypedAnimation。

**参数：**
- `UUID` - 实体的 UUID
- `animationId` - 动画 ID，由 `registerTypedAnimation` 方法返回
- `transTime` - 动画的过渡时间（单位：tick）

**返回值：**
- 如果动画成功开始播放，则返回 `true`；否则返回 `false`

**示例：**
```java
api.playTypedAnimation("123e4567-e89b-12d3-a456-426614174000", "minecraft:entity.zombie.attack", 10);
```

#### `playAnimation(String UUID, String namespace, String animationName, int transTime, float speed, float speedChangeDuration)`

播放模型动画，并可以控制动画的播放速度。

**参数：**
- `UUID` - 实体的 UUID
- `namespace` - 动画的命名空间（默认为 "player"）
- `animationName` - 要播放的动画名称
- `transTime` - 动画的过渡时间（单位：tick）
- `speed` - 动画的播放速度（1.0 为正常速度）
- `speedChangeDuration` - 速度变化的持续时间（单位：秒）

**返回值：**
- 如果动画成功开始播放，则返回 `true`；否则返回 `false`

**示例：**
```java
// 以 1.5 倍速度播放攻击动画，持续 10 秒
api.playAnimation("123e4567-e89b-12d3-a456-426614174000", "minecraft", "attack", 10, 1.5f, 1.0f);

// 以 0.5 倍慢速播放行走动画
api.playAnimation("123e4567-e89b-12d3-a456-426614174000", "minecraft", "walk", 10, 0.5f, 1.0f);
```

#### `registerEntityAnimationOverride(String UUID, String animationStateKey, String animationId)`

为实体的特定动画状态注册一个覆盖动画。

**参数：**
- `UUID` - 实体的 UUID
- `animationStateKey` - 动画状态的键（例如 "walk", "attack"）
- `animationId` - 要播放的动画的资源路径

**示例：**
```java
api.registerEntityAnimationOverride("123e4567-e89b-12d3-a456-426614174000", "attack", "minecraft:entity.zombie.attack");
```

### 碰撞系统

#### `createCollisionBoxBoundToBone(String modelId, String boneName, String cbName, float sizeX, float sizeY, float sizeZ, float offsetX, float offsetY, float offsetZ)`

创建绑定到骨骼的碰撞盒，并同步到客户端。

**参数：**
- `modelId` - 模型ID，可以是实体ID、UUID、自定义名称或标签
- `boneName` - 骨骼名称，碰撞盒将跟随此骨骼运动
- `cbName` - 碰撞回调的名称
- `sizeX`, `sizeY`, `sizeZ` - 碰撞盒的尺寸（单位：米）
- `offsetX`, `offsetY`, `offsetZ` - 相对于骨骼原点的偏移量（单位：米）

**返回值：**
- 碰撞盒的唯一标识符，如果创建失败则返回 `null`

**示例：**
```java
String boxId = api.createCollisionBoxBoundToBone("npc123", "RightHand", "sword_attack", 1.0f, 1.0f, 2.0f, 0.5f, 0.0f, 0.0f);
```

#### `addCollisionCallback(String collisionBoxId, String cbName, String UUID, Boolean autoReset, Integer ticks)`

为碰撞盒添加碰撞回调。

**参数：**
- `collisionBoxId` - 碰撞盒的ID
- `cbName` - 回调的唯一名称
- `UUID` - 实体的UUID
- `autoReset` - 是否在触发后自动重置攻击系统状态
- `ticks` - 攻击判定的有效持续时间（单位：tick）

**示例：**
```java
api.addCollisionCallback("RightHand_123", "sword_hit", "123e4567-e89b-12d3-a456-426614174000", true, 20);
```

#### `shouldTurnBody(String modelId, boolean shouldTurnBody, boolean shouldTurnHead)`

控制实体的身体和头部转向行为。

**参数：**
- `modelId` - 实体的唯一标识符（可以是实体ID、UUID、自定义名称或标签）
- `shouldTurnBody` - 是否允许身体转向
- `shouldTurnHead` - 是否允许头部转向

**示例：**
```java
api.shouldTurnBody("npc123", true, false); // 允许身体转向，但不允许头部转向
```

#### `resetEntityByCbname(String UUID, String cbName, String collisionBoxId)`

根据回调名称重置实体的攻击系统。

**参数：**
- `UUID` - 实体的UUID
- `cbName` - 回调名称
- `collisionBoxId` - 碰撞盒ID

**示例：**
```java
api.resetEntityByCbname("123e4567-e89b-12d3-a456-426614174000", "sword_hit", "RightHand_123");
```

#### `getContactPosA(long manifoldId)` 和 `getContactPosB(long manifoldId)`

获取碰撞点的位置信息。

**参数：**
- `manifoldId` - 碰撞点ID

**返回值：**
- 返回 `double[]` 数组，包含 [x, y, z] 坐标

**示例：**
```java
double[] posA = api.getContactPosA(manifoldId);
double[] posB = api.getContactPosB(manifoldId);
System.out.println("碰撞点A: " + Arrays.toString(posA));
System.out.println("碰撞点B: " + Arrays.toString(posB));
```

## 最佳实践

1. **性能考虑**：
   - 避免在每帧中频繁调用API方法
   - 使用唯一标识符（如UUID）来引用实体，以提高查找效率

2. **错误处理**：
   - 始终检查API方法的返回值
   - 使用try-catch块处理可能的异常

3. **内存管理**：
   - 及时移除不再需要的碰撞回调和动画覆盖
   - 避免创建过多的碰撞盒和动画实例

## 常见问题

### 1. 如何查找实体的UUID？

可以通过游戏内命令或调试信息获取实体的UUID。

### 2. 动画不播放怎么办？
- 检查动画ID是否正确
- 确保实体支持动画
- 检查是否有其他动画覆盖了当前动画

### 3. 碰撞回调未触发？
- 确认碰撞盒已正确创建
- 检查碰撞盒的大小和位置是否合适
- 确保碰撞盒的碰撞层设置正确

## 版本历史

| 版本 | 日期 | 描述 |
|------|------|------|
| 1.0.0 | 2025-06-18 | 初始版本 |