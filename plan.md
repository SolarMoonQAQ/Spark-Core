# Spark-Core 中实现 UE5 受击运动匹配逻辑开发计划

## 1. 引言

### 1.1. 目标

在 Spark-Core 项目中，基于 `UE5 受击运动计算.md` 文档的核心思想，实现一套完整的受击响应系统，模拟 UE5 中的物理碰撞、动画触发、物理-动画混合以及 IK 姿态修正流程，以提升实体受击表现的真实感和交互性。

### 1.2. 背景

Spark-Core 已具备物理引擎 (Bullet)、动画系统、技能系统和 IK 集成的基础。本项目旨在将 UE5 中成熟的受击响应管线概念引入 Spark-Core，利用并扩展现有系统，实现更高级的角色物理交互。

## 2. UE5 核心概念与 Spark-Core 映射

| UE5 概念                               | Spark-Core 对应/实现思路                                                                                                                               |
| :------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Chaos Physics** (物理引擎)           | **Spark Physics (Bullet)**: 利用 `PhysicsLevel`, `PhysicsHost`, `PhysicsRigidBody` 处理碰撞检测和物理模拟。                                                |
| **Skeletal Mesh Animation System**     | **Spark Animation System**: 利用 `IAnimatable`, `AnimController`, `AnimInstance`, `BoneGroup` 管理和播放受击动画、状态转换。                               |
| **Physics-Animation Blending**         | **扩展/新建混合机制**: 可能需要扩展 `MoveWithAnimatedBoneTicker` 或创建新的混合管理器，实现类似 UE5 的物理权重控制 (`Set All Bodies Below Physics Blend Weight`)。 |
| **IK / Control Rig** (反向动力学)      | **Spark-Core 现有 IK 系统**: 利用已集成的 IK 功能，在物理和动画处理后进行姿态修正（如脚部贴地、平衡恢复）。                                                |

## 3. 分步实施计划

### 3.1. 物理集成：碰撞检测与力施加

*   **任务**: 
    *   实现精确的受击碰撞检测（可研究扩展 `AttackCollisionCallback` 或创建专用回调）。
    *   在检测到碰撞后，向 `PhysicsRigidBody` 施加合适的冲击力或速度变化，模拟物理冲击效果。
    *   考虑引入物理材质概念（如果尚不支持），影响碰撞响应。
*   **涉及/需创建**: `PhysicsHostHelper`, `PhysicsRigidBody`, `PhysicsLevel`, 新/扩展的碰撞回调类。

### 3.2. 动画集成：状态管理与触发

*   **任务**: 
    *   设计或扩展 `AnimController` 或利用 Skill System 实现受击动画状态机（例如：轻微受击、踉跄、倒地、起身）。
    *   根据物理碰撞结果（冲击力大小、方向、部位）触发相应的动画状态转换。
    *   定义动画优先级、打断和混合逻辑。
*   **涉及/需创建**: `AnimController`, `IAnimatable`, `SkillType` (可选), 可能需要新的动画状态管理类/逻辑。

### 3.3. 物理-动画混合实现

*   **任务**: 
    *   设计并实现物理模拟权重控制机制，允许在纯物理驱动（布娃娃）和动画驱动之间平滑过渡。
    *   确定触发权重变化的条件（如受击强度阈值、时间、特定动画事件）。
    *   研究 `MoveWithAnimatedBoneTicker` 是否可用于此目的，或需要更专门的解决方案。
*   **涉及/需创建**: `AnimController`, `PhysicsHostHelper`, `PhysicsRigidBody`, 新的混合管理类或对现有类的修改。

### 3.4. IK 集成：姿态修正与稳定

*   **任务**: 
    *   将现有 IK 系统接入受击响应流程。
    *   在物理模拟和动画播放（特别是混合状态下）之后，应用 IK 约束（如脚部贴地、维持平衡）来修正最终的角色姿态。
    *   处理 IK 与物理模拟、动画状态之间的优先级和冲突。
*   **涉及/需创建**: 现有的 IK 相关类 (需确认具体名称), `AnimController`, 新的 IK 应用逻辑。

## 4. 涉及/需创建的文件和函数（初步列表）

*   **物理**: 
    *   `HitReactionCollisionCallback.kt` (新): 处理受击碰撞检测。
    *   修改 `PhysicsRigidBody`: 可能增加用于受击响应的接口或状态。
    *   修改 `PhysicsHostHelper`: 可能增加施加受击力的辅助函数。
*   **动画**: 
    *   修改 `AnimController`: 增加受击状态管理、状态转换逻辑。
    *   可能创建 `HitReactionAnimState.kt` (新): 定义具体的受击动画状态。
*   **混合**: 
    *   `PhysicsAnimationBlender.kt` (新): 管理物理和动画的混合权重及过渡。
    *   修改 `MoveWithAnimatedBoneTicker` 或创建替代方案。
*   **IK**: 
    *   修改现有 IK 控制器/处理器: 增加受击后的姿态修正逻辑。
*   **核心**: 
    *   `HitReactionManager.kt` (新): 可能需要一个中心管理器来协调物理、动画、混合和 IK。

## 5. 测试策略

*   **单元测试**: 对碰撞检测、力施加、状态转换、混合权重计算、IK 修正等关键逻辑进行单元测试。
*   **集成测试**: 在游戏内创建测试场景，模拟不同强度、方向、部位的受击事件，观察物理响应、动画表现、混合效果和 IK 修正是否符合预期。
*   **调试工具**: 开发可视化调试工具，用于实时查看碰撞信息、物理状态、动画状态、混合权重、IK 目标等。
