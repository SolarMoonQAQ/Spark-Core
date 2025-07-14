# JavaScript API 参考

## 1. 概述

SparkCore 提供了丰富的 JavaScript API，基于 Mozilla Rhino 引擎实现。这些 API 允许开发者通过 JavaScript 脚本动态控制游戏内的各种功能，包括动画、技能、物理碰撞、实体操作等。

### 1.1 API 系统架构

**位置**: `cn.solarmoon.spark_core.js.JSApi`

JavaScript API 系统采用模块化设计，每个 API 模块负责特定的功能领域：

```kotlin
interface JSApi {
    val id: String                      // API 模块唯一标识
    val valueCache: MutableMap<String, String>  // 脚本缓存
    val scriptCount: Int                // 脚本数量
    
    fun onLoad()                        // 加载完成回调
    fun onReload()                      // 重载回调
}
```

### 1.2 支持的 API 模块

| API 模块 | 功能描述 | 主要用途 |
|---------|---------|---------|
| `skill` | 技能系统 | 创建和管理自定义技能 |
| `animation` | 动画系统 | 控制实体动画播放 |
| `physics` | 物理系统 | 碰撞检测和物理交互 |
| `entity` | 实体系统 | 实体操作和管理 |
| `ik` | IK 约束系统 | 逆向运动学约束 |

## 2. 脚本执行环境

### 2.1 SparkJS 核心

**位置**: `cn.solarmoon.spark_core.js.SparkJS`

SparkJS 是所有 JavaScript 执行的核心基类，提供线程安全的脚本执行环境：

```kotlin
abstract class SparkJS {
    val context = Context.enter()           // Rhino 上下文
    val scope = context.initStandardObjects() // 全局作用域
    
    open fun loadAllFromRegistry()          // 从注册表加载脚本
    open fun reloadScript(script: OJSScript) // 重载指定脚本
    open fun unloadScript(apiId: String, fileName: String) // 卸载脚本
    protected open fun executeScript(script: OJSScript) // 执行脚本
}
```

### 2.2 客户端和服务端执行

**客户端执行器**: `cn.solarmoon.spark_core.js.ClientSparkJS`
**服务端执行器**: `cn.solarmoon.spark_core.js.ServerSparkJS`

- **线程安全**: 每个执行器维护独立的 Rhino 上下文
- **脚本同步**: 服务端脚本变更自动同步到客户端
- **热重载**: 支持运行时脚本重载而无需重启

## 3. 技能系统 API (skill)

### 3.1 基础用法

**API 模块**: `cn.solarmoon.spark_core.js.skill.JSSkillApi`

```javascript
// 创建基础技能
Skill.create("spark_core:custom_skill", builder => {
    builder.setPriority(100);
    builder.setMaxCooldown(20 * 5); // 5秒冷却
    builder.accept(skill => {
        // 技能执行逻辑
        skill.getUser().sendMessage("技能已激活!");
    });
});

// 创建继承技能
Skill.createBy("spark_core:advanced_skill", "spark_core:base_skill", builder => {
    builder.setPriority(150);
    builder.accept(skill => {
        // 继承并扩展父技能功能
        skill.getParent().execute(); // 执行父技能
        // 添加新功能
    });
});
```

### 3.2 JSSkillTypeBuilder

**位置**: `cn.solarmoon.spark_core.js.skill.JSSkillTypeBuilder`

```javascript
// 技能配置方法
builder.setPriority(priority);              // 设置优先级
builder.setMaxCooldown(ticks);              // 设置冷却时间
builder.setManaCost(cost);                  // 设置魔法消耗
builder.setRequirement(condition);          // 设置使用条件
builder.accept(skillFunction);              // 设置技能执行函数
builder.onStart(startFunction);             // 设置开始执行回调
builder.onComplete(completeFunction);       // 设置完成回调
builder.onCancel(cancelFunction);           // 设置取消回调
```

### 3.3 技能生命周期

```javascript
Skill.create("spark_core:lifecycle_skill", builder => {
    builder.onStart(skill => {
        console.log("技能开始执行");
    });
    
    builder.accept(skill => {
        console.log("技能主要逻辑");
    });
    
    builder.onComplete(skill => {
        console.log("技能执行完成");
    });
    
    builder.onCancel(skill => {
        console.log("技能被取消");
    });
});
```

## 4. 动画系统 API

### 4.1 JSAnimHelper

**位置**: `cn.solarmoon.spark_core.js.extension.JSAnimHelper`

```javascript
// 播放动画
AnimHelper.playAnimation(entityId, "minecraft:attack", 20, 1.0, 0.5);

// 切换模型
AnimHelper.changeModel(entityId, "minecraft:zombie");

// 控制身体转向
AnimHelper.shouldTurnBody(entityId, true, false);
```

### 4.2 JSAnimation

**位置**: `cn.solarmoon.spark_core.js.extension.JSAnimation`

```javascript
// 创建动画实例
let animation = new JSAnimation("minecraft:walk");

// 设置动画参数
animation.setSpeed(1.5);
animation.setLoop(true);
animation.setBlendTime(10);

// 播放动画
animation.play(entity);
```

### 4.3 JSAnimatable

**位置**: `cn.solarmoon.spark_core.js.extension.JSAnimatable`

```javascript
// 获取实体动画能力
let animatable = entity.getAnimatable();

// 播放动画
animatable.playAnimation("attack", 20);

// 设置动画速度
animatable.setSpeed(2.0);

// 停止动画
animatable.stopAnimation();
```

## 5. 物理系统 API

### 5.1 JSPhysicsHelper

**位置**: `cn.solarmoon.spark_core.js.extension.JSPhysicsHelper`

```javascript
// 创建碰撞盒
let collisionBoxId = PhysicsHelper.createCollisionBox(
    entityId,       // 实体ID
    "rightHand",    // 骨骼名称
    "attack_box",   // 碰撞盒名称
    1.0, 1.0, 1.0,  // 尺寸 (x, y, z)
    0.0, 0.0, 0.0   // 偏移 (x, y, z)
);

// 添加碰撞回调
PhysicsHelper.addCollisionCallback(
    collisionBoxId,
    "on_hit",
    entityId,
    true,  // 自动重置
    20     // 持续时间 (ticks)
);

// 获取碰撞位置
let contactPosA = PhysicsHelper.getContactPosA(manifoldId);
let contactPosB = PhysicsHelper.getContactPosB(manifoldId);
```

### 5.2 JSPhysicsCollisionObject

**位置**: `cn.solarmoon.spark_core.js.extension.JSPhysicsCollisionObject`

```javascript
// 创建物理对象
let pco = new JSPhysicsCollisionObject(entity, shape);

// 设置物理属性
pco.setKinematic(true);
pco.setContactResponse(false);
pco.setGravity(0, 0, 0);

// 添加物理回调
pco.addCollisionCallback(callback);

// 添加物理Ticker
pco.addTicker(ticker);
```

## 6. 实体系统 API

### 6.1 JSEntity

**位置**: `cn.solarmoon.spark_core.js.extension.JSEntity`

```javascript
// 基础实体操作
entity.setPosition(x, y, z);
entity.setRotation(yaw, pitch);
entity.setHealth(health);
entity.setMaxHealth(maxHealth);

// 实体状态
if (entity.isAlive()) {
    entity.sendMessage("实体还活着");
}

// 实体伤害
entity.damage(damageAmount, damageSource);

// 实体移动
entity.moveRelative(speed, strafe, up, forward);
```

### 6.2 JSEntityHelper

**位置**: `cn.solarmoon.spark_core.js.extension.JSEntityHelper`

```javascript
// 查找实体
let entity = EntityHelper.findEntity(entityId);
let entitiesNearby = EntityHelper.findEntitiesNearby(x, y, z, radius);

// 创建实体
let newEntity = EntityHelper.createEntity(entityType, level, x, y, z);

// 实体关系
EntityHelper.setTarget(hunter, target);
EntityHelper.clearTarget(hunter);
```

## 7. IK 约束系统 API

### 7.1 JSIKApi

**位置**: `cn.solarmoon.spark_core.js.ik.JSIKApi`

```javascript
// 创建 IK 约束
IK.create("spark_core:look_at_constraint", builder => {
    builder.setType("LOOK_AT");
    builder.setTarget(targetEntity);
    builder.setBone("head");
    builder.setStrength(1.0);
});

// 创建 IK 组件类型
IK.createComponentType("spark_core:custom_ik", builder => {
    builder.setComponentClass(CustomIKComponent.class);
    builder.setSerializer(CustomIKSerializer.class);
});
```

### 7.2 JSIKHostAPI

**位置**: `cn.solarmoon.spark_core.js.ik.JSIKHostAPI`

```javascript
// 获取 IK 主机
let ikHost = entity.getIKHost();

// 添加 IK 组件
ikHost.addComponent("spark_core:look_at_constraint");

// 移除 IK 组件
ikHost.removeComponent("spark_core:look_at_constraint");

// 获取 IK 组件
let component = ikHost.getComponent("spark_core:look_at_constraint");
```

## 8. 工具类 API

### 8.1 JSMath

**位置**: `cn.solarmoon.spark_core.js.extension.JSMath`

```javascript
// 数学运算
let distance = Math.distance(x1, y1, z1, x2, y2, z2);
let angle = Math.angle(from, to);
let interpolated = Math.lerp(start, end, alpha);

// 向量运算
let normalized = Math.normalize(vector);
let dotProduct = Math.dot(vec1, vec2);
let crossProduct = Math.cross(vec1, vec2);
```

### 8.2 JSLogger

**位置**: `cn.solarmoon.spark_core.js.extension.JSLogger`

```javascript
// 日志输出
Logger.info("信息日志");
Logger.warn("警告日志");
Logger.error("错误日志");
Logger.debug("调试日志");

// 格式化日志
Logger.info("玩家 {} 使用技能 {}", playerName, skillName);
```

### 8.3 JSLevel

**位置**: `cn.solarmoon.spark_core.js.extension.JSLevel`

```javascript
// 世界操作
let level = entity.getLevel();

// 方块操作
let block = level.getBlock(x, y, z);
level.setBlock(x, y, z, blockState);

// 实体搜索
let entities = level.getEntitiesOfClass(EntityType.PLAYER, boundingBox);

// 世界属性
let isClientSide = level.isClientSide();
let dayTime = level.getDayTime();
```

## 9. 脚本生命周期管理

### 9.1 脚本注册和加载

```javascript
// 脚本自动注册到对应的 API 模块
// 文件路径: sparkcore/namespace/scripts/skill/my_skill.js

// 脚本内容会自动加载并执行
Skill.create("namespace:my_skill", builder => {
    // 技能定义
});
```

### 9.2 脚本热重载

```javascript
// 脚本修改后自动重载
// 重载时会调用 API 的 onReload() 方法
// 清理旧的注册并重新执行脚本
```

### 9.3 脚本同步

```javascript
// 服务端脚本变更自动同步到客户端
// 客户端接收到脚本更新后重新执行
// 保证服务端和客户端脚本的一致性
```

## 10. 错误处理和调试

### 10.1 脚本错误处理

```javascript
try {
    // 可能出错的代码
    entity.someMethod();
} catch (error) {
    Logger.error("脚本执行错误: " + error.message);
}
```

### 10.2 调试技巧

```javascript
// 使用日志调试
Logger.debug("当前实体位置: " + entity.getPosition());

// 检查对象状态
if (entity == null) {
    Logger.warn("实体对象为空");
    return;
}

// 性能监控
let startTime = System.currentTimeMillis();
// 执行代码
let endTime = System.currentTimeMillis();
Logger.debug("执行时间: " + (endTime - startTime) + "ms");
```

## 11. 最佳实践

### 11.1 性能优化

```javascript
// 避免在循环中创建对象
let position = new Vector3();
for (let entity of entities) {
    entity.getPosition(position); // 复用对象
}

// 使用合适的数据结构
let entityCache = new Map(); // 而不是 Object

// 避免频繁的 null 检查
if (entity?.isAlive?.()) {
    // 使用可选链操作符
}
```

### 11.2 代码组织

```javascript
// 使用命名空间避免冲突
(function(namespace) {
    namespace.MySkill = function() {
        // 技能实现
    };
})(this.MyMod = this.MyMod || {});

// 模块化代码
function createBasicSkill(id, config) {
    return Skill.create(id, builder => {
        builder.setPriority(config.priority || 100);
        builder.setMaxCooldown(config.cooldown || 60);
        builder.accept(config.execute);
    });
}
```

### 11.3 错误处理

```javascript
// 总是检查必要的前置条件
function executeSkill(entity, skillId) {
    if (!entity || !entity.isAlive()) {
        Logger.warn("无效的实体状态");
        return false;
    }
    
    let skill = SkillManager.getSkill(skillId);
    if (!skill) {
        Logger.error("技能不存在: " + skillId);
        return false;
    }
    
    return skill.execute(entity);
}
```

## 12. 版本兼容性

### 12.1 API 版本

当前 JavaScript API 版本: `1.0.0`

### 12.2 兼容性说明

- **向后兼容**: 新版本保持对旧脚本的兼容性
- **废弃警告**: 废弃的 API 会在日志中显示警告
- **迁移指南**: 重大变更会提供迁移指南

---

## 总结

SparkCore 的 JavaScript API 提供了强大而灵活的脚本开发能力，支持技能、动画、物理、实体等多个系统的深度集成。通过合理使用这些 API，开发者可以创建丰富的游戏内容和交互体验。

建议开发者：
1. 熟悉基础 API 结构和生命周期
2. 遵循最佳实践进行性能优化
3. 合理使用错误处理和日志系统
4. 关注版本更新和兼容性变化