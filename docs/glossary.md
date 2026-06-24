# 项目术语表

> 生成时间：2026-04-29
> 项目：Spark-Core（一个 Minecraft NeoForge Mod 核心库）

## 概念列表

### PhysicsSystem（物理系统）

- **职责**：提供基于 JBullet 的实时物理模拟框架
- **描述**：为 Minecraft 世界提供刚体物理、地形碰撞检测、物理步进调度等能力。使用独立物理线程 + 协程驱动，支持动态步进频率调节。物理系统通过 `PhysicsHost` 接口注入到 Entity/Level 等对象中。
- **关键类**：
  - `cn.solarmoon.spark_core.physics.PhysicsHost` — 物理宿主接口，Level/Entity 通过实现此接口获得物理能力
  - `cn.solarmoon.spark_core.physics.level.PhysicsLevel` — 抽象物理世界，管理物理空间、步进调度、协程生命周期
  - `cn.solarmoon.spark_core.physics.level.ClientPhysicsLevel` — 客户端物理世界实现
  - `cn.solarmoon.spark_core.physics.level.ServerPhysicsLevel` — 服务端物理世界实现
  - `cn.solarmoon.spark_core.physics.level.PhysicsWorld` — 物理世界（JBullet PhysicsSpace 封装）
  - `cn.solarmoon.spark_core.physics.terrain.PhysicsChunkManager` — 地形区块碰撞管理器
  - `cn.solarmoon.spark_core.physics.terrain.BlockShapeManager` — 方块形状管理器
  - `cn.solarmoon.spark_core.physics.body.CollisionFuncApplier` — 碰撞回调应用器

---

### GAS（Gameplay Ability System，游戏技能系统）

- **职责**：定义技能授予、激活、取消、结束的完整生命周期框架
- **描述**：参考 Unreal Engine GAS 设计，为实体（Entity）提供可扩展的技能系统。核心概念包括 `AbilityType`（技能类型定义）、`Ability`（技能实例/运行时状态）、`AbilitySpec`（技能容器，持有动态参数）、`AbilitySystemComponent`（ASC，技能系统组件，管理所有技能）。支持服务端授权 + 客户端预测的激活模式。
- **关键类**：
  - `cn.solarmoon.spark_core.gas.Ability` — 技能抽象类，定义技能的运行时状态和生命周期方法
  - `cn.solarmoon.spark_core.gas.AbilityType` — 技能类型，定义技能的元数据、实例化策略
  - `cn.solarmoon.spark_core.gas.AbilitySpec` — 技能容器，持有 AbilityType 引用和动态参数（如等级、蓝耗）
  - `cn.solarmoon.spark_core.gas.AbilitySystemComponent` — 技能系统组件，管理技能授予/激活/结束以及事件监听
  - `cn.solarmoon.spark_core.gas.AbilityHost` — 技能宿主接口，Entity 实现此接口以支持 GAS
  - `cn.solarmoon.spark_core.gas.AbilityTask` — 技能任务，将技能逻辑拆分为可组合的子任务
  - `cn.solarmoon.spark_core.gas.ActivationContext` — 技能激活上下文，传递激活时的参数
  - `cn.solarmoon.spark_core.gas.GameplayTag` — 游戏标签，用于技能事件过滤和分类
  - `cn.solarmoon.spark_core.gas.GameplayTagContainer` — 游戏标签容器
  - `cn.solarmoon.spark_core.gas.InstancingPolicy` — 实例化策略（每 Actor 单例 / 每次执行新实例）

---

### StateMachine（状态机）

- **职责**：提供基于 DSF（Data-Driven State Machine）的图结构状态机框架
- **描述**：通过声明式方式定义状态节点、转移条件和行为动作。支持 Codec 序列化/反序列化，可从 JSON 数据驱动。预置了玩家基础动画状态机和实体基础使用动画状态机。
- **关键类**：
  - `cn.solarmoon.spark_core.state_machine.IStateMachineHolder` — 状态机持有者接口，Entity 实现此接口以持有状态机
  - `cn.solarmoon.spark_core.state_machine.StateMachineHandler` — 状态机处理器，管理单个状态机的激活和推进
  - `cn.solarmoon.spark_core.state_machine.graph.StateMachineGraph` — 状态机图定义（节点 + 初始节点）
  - `cn.solarmoon.spark_core.state_machine.graph.StateNode` — 状态节点，包含该状态的 Action 列表
  - `cn.solarmoon.spark_core.state_machine.graph.StateTransition` — 状态转移定义（源状态 → 目标状态 + 条件）
  - `cn.solarmoon.spark_core.state_machine.graph.StateCondition` — 转移条件接口
  - `cn.solarmoon.spark_core.state_machine.graph.StateAction` — 状态动作接口（进入/退出/tick 动作）
  - `cn.solarmoon.spark_core.state_machine.graph.StateGraphController` — 状态图运行时控制器，驱动状态流转
  - `cn.solarmoon.spark_core.state_machine.presets.PlayerBaseAnimStateMachine` — 玩家基础动画状态机预置
  - `cn.solarmoon.spark_core.state_machine.presets.EntityBaseUseAnimStateMachine` — 实体基础使用动画状态机预置

---

### SpreadingSoundSystem（传播音效系统）

- **职责**：实现具有音速传播延迟和多普勒效应的高级音效系统
- **描述**：传统 Minecraft 音效是瞬间全范围播放，本系统模拟声音以音速从声源向四周扩散的过程，收听者会在声音"到达"时才听到。支持动态声源、多通道复合声源（如引擎多工况音轨）、音效过渡（CrossFade）、淡入淡出控制。
- **关键类**：
  - `cn.solarmoon.spark_core.sound.ISoundSpreader` — 动态声源接口，定义实时位置/速度/音量/音高获取方法
  - `cn.solarmoon.spark_core.sound.IMultiChannelSoundSpreader` — 多通道动态声源接口，支持多个声音通道同时播放
  - `cn.solarmoon.spark_core.sound.ISpreadingSoundPlayer` — 传播音效播放器接口，定义播放/过渡/停止操作
  - `cn.solarmoon.spark_core.sound.SpreadingSoundInstance` — 传播音效实例（SoundInstance 实现）
  - `cn.solarmoon.spark_core.sound.SoundData` — 音效数据封装
  - `cn.solarmoon.spark_core.sound.SoundSourcePoint` — 声源点数据（位置、速度、音高、音量、传播距离）
  - `cn.solarmoon.spark_core.api.SpreadingSoundHelper` — 传播音效工具类
  - `cn.solarmoon.spark_core.sound.ClientSpreadingSoundPlayer` — 客户端传播音效播放器
  - `cn.solarmoon.spark_core.sound.ServerSpreadingSoundPlayer` — 服务端传播音效播放器
  - `cn.solarmoon.spark_core.sound.payload.SpreadingSoundPayload` — 传播音效网络包
  - `cn.solarmoon.spark_core.sound.payload.SpreadingSoundFadePayload` — 淡出网络包
  - `cn.solarmoon.spark_core.sound.payload.SpreadingSoundStopPayload` — 停止网络包

---

### SyncSystem（同步系统）

- **职责**：提供可注册的同步数据类型框架，支持网络序列化
- **描述**：通过注册中心（Registry）管理多种同步数据类型，每种类型拥有独立的 Codec（用于持久化/命令）和 StreamCodec（用于网络传输）。`Syncer` 接口被 Entity/BlockEntity 等实现，表明其具备同步能力。
- **关键类**：
  - `cn.solarmoon.spark_core.sync.Syncer` — 同步器接口，标记对象具有同步能力
  - `cn.solarmoon.spark_core.sync.SyncerType` — 同步器类型，关联具体的数据编解码器
  - `cn.solarmoon.spark_core.sync.SyncData` — 同步数据接口，定义数据的 Codec 和 StreamCodec
  - `cn.solarmoon.spark_core.sync.IntSyncData` — 整数类型同步数据
  - `cn.solarmoon.spark_core.sync.BlockPosSyncData` — 方块坐标同步数据

---

### DeltaSync（差异同步）

- **职责**：基于差异（Diff）的字段级数据同步方案
- **描述**：通过注解标记需要同步的字段，自动生成差异快照和补丁包，实现高效的增量同步，减少网络传输量。
- **关键类**：
  - `cn.solarmoon.spark_core.delta_sync.DiffSyncSchema` — 差异同步模式定义
  - `cn.solarmoon.spark_core.delta_sync.DiffSyncFieldAnnotation` — 标记需要同步的字段的注解
  - `cn.solarmoon.spark_core.delta_sync.DiffPacket` — 差异网络包
  - `cn.solarmoon.spark_core.delta_sync.DiffSnapshot` — 差异快照
  - `cn.solarmoon.spark_core.delta_sync.FieldDef` — 字段定义

---

### ContentPackSystem（内容包系统）

- **职责**：提供基于"内容包（Content Pack）"的模块化资源加载框架
- **描述**：Spark-Core 定义了一套独立于 Minecraft 原版资源包的内容包系统。内容包以 `.zip` 或目录形式存放在 `spark_modules/` 目录下，每个包包含 `meta.json` 元数据（定义 ID、名称、描述、版本、依赖关系）。通过 `SparkPackLoader` 扫描并解析包结构，将文件按 `namespace → module → path` 组织，然后分派给对应 `SparkPackModule` 处理。支持服务端向客户端同步包内容，以及客户端本地加载。通过 `SparkVirtualResourcePack` 将内容注入到原版资源包管线中，支持 `/spark:package` 命令热重载。
- **关键类**：
  - `cn.solarmoon.spark_core.pack.SparkPackLoader` — 包加载器，扫描目录、构建依赖图、按模块派发读取
  - `cn.solarmoon.spark_core.pack.graph.SparkPackage` — 包数据结构（元数据 + 条目映射）
  - `cn.solarmoon.spark_core.pack.graph.SparkPackGraph` — 包依赖图，解析拓扑排序加载顺序
  - `cn.solarmoon.spark_core.pack.graph.SparkPackMetaInfo` — 包元数据（ID、名称、依赖、冲突等）
  - `cn.solarmoon.spark_core.pack.modules.SparkPackModule` — 模块接口，定义 `onStart/read/onFinish` 生命周期
  - `cn.solarmoon.spark_core.pack.modules.ReadMode` — 读取模式枚举（SERVER_TO_CLIENT / LOCAL_ONLY / CLIENT_LOCAL_ONLY）
  - `cn.solarmoon.spark_core.pack.modules.ModelModule` — 模型模块，解析基岩版几何数据
  - `cn.solarmoon.spark_core.pack.modules.AnimationModule` — 动画模块，解析基岩版动画数据
  - `cn.solarmoon.spark_core.pack.modules.AnimStateModule` — 动画状态机模块，解析动画控制器数据
  - `cn.solarmoon.spark_core.pack.modules.RecipeModule` — 配方模块
  - `cn.solarmoon.spark_core.pack.modules.LangModule` — 语言模块，动态注入 i18n 翻译
  - `cn.solarmoon.spark_core.pack.modules.TextureModule` — 纹理模块
  - `cn.solarmoon.spark_core.pack.modules.FontModule` — 字体模块
  - `cn.solarmoon.spark_core.pack.modules.AbilityTypeModule` — 技能类型模块
  - `cn.solarmoon.spark_core.pack.modules.SoundModule` — 音效模块
  - `cn.solarmoon.spark_core.pack.SparkVirtualResourcePack` — 虚拟资源包，将内容注入原版资源管线
  - `cn.solarmoon.spark_core.pack.SparkPackLoaderApplier` — 加载器应用器，监听事件触发加载流程
  - `cn.solarmoon.spark_core.pack.SparkPackResourceLoader` — 包资源读取器，处理内置资源和自动打包
  - `cn.solarmoon.spark_core.pack.readable.ReadablePathType` — 可读路径类型接口（zip/目录）
  - `cn.solarmoon.spark_core.pack.readable.ReadableZip` — Zip 包读取实现
  - `cn.solarmoon.spark_core.pack.readable.ReadableDirectory` — 目录读取实现
  - `cn.solarmoon.spark_core.pack.sync.SparkPackagePayload` — 包同步网络包
  - `cn.solarmoon.spark_core.pack.sync.SparkPackageSendingTask` — 包发送任务（配置阶段）
  - `cn.solarmoon.spark_core.pack.sync.SparkPackageReloadPayload` — 包重载网络包
  - `cn.solarmoon.spark_core.registry.common.SparkPackModuleRegister` — 模块注册入口，注册所有内置模块

---

### PatchSystem（补丁系统）

- **职责**：通过接口 + Mixin 实现对 Minecraft 核心类的安全扩展
- **描述**：定义 `LevelPatch`、`EntityPatch`、`BlockEntityPatch` 等接口，通过 Mixin 注入到原版类中。不直接使用 Interface Injection 以避免与 Sodium 等 Mod 冲突，而是通过运行时 cast 访问。`SparkLevel` API 类提供统一的安全访问入口。
- **关键类**：
  - `cn.solarmoon.spark_core.LevelPatch` — Level 补丁接口，组合 PhysicsHost + TaskSubmitOffice
  - `cn.solarmoon.spark_core.EntityPatch` — Entity 补丁接口，组合 PhysicsHost + HurtDataHolder + IStateMachineHolder + AbilityHost + Syncer
  - `cn.solarmoon.spark_core.BlockEntityPatch` — BlockEntity 补丁接口，实现 Syncer
  - `cn.solarmoon.spark_core.api.SparkLevel` — Level 扩展能力的统一安全访问入口

---

### InputBuffer（输入缓冲）

- **职责**：提供可配置的输入缓冲机制，提升操作手感
- **描述**：当玩家在特定时间窗口内按下某个键但当时无法执行操作时，输入缓冲会"记住"该输入，在可执行时自动触发。支持多种触发模式。
- **关键类**：
  - `cn.solarmoon.spark_core.input_buffer.InputBuffer` — 输入缓冲核心类
  - `cn.solarmoon.spark_core.input_buffer.InputBufferTriggerMode` — 缓冲触发模式枚举
  - `cn.solarmoon.spark_core.input_buffer.InputEvent` — 输入事件

---

### VisualEffectSystem（视觉效果系统）

- **职责**：提供拖尾/轨迹渲染、摄像机抖动等视觉增强效果
- **描述**：包含基于网格（Mesh）的拖尾渲染系统（TrailRenderer）和摄像机抖动系统（CameraShaker），通过独立的渲染器实现。
- **关键类**：
  - `cn.solarmoon.spark_core.visual_effect.trail.TrailRenderer` — 拖尾渲染器
  - `cn.solarmoon.spark_core.visual_effect.trail.TrailMesh` — 拖尾网格
  - `cn.solarmoon.spark_core.visual_effect.trail.TrailPoint` — 拖尾轨迹点
  - `cn.solarmoon.spark_core.visual_effect.trail.TrailInfo` — 拖尾配置信息
  - `cn.solarmoon.spark_core.visual_effect.camera_shake.CameraShaker` — 摄像机抖动器
  - `cn.solarmoon.spark_core.visual_effect.camera_shake.CameraShakeApplier` — 摄像机抖动应用器
  - `cn.solarmoon.spark_core.visual_effect.FovHelper` — 视场角辅助类

---

### BehaviorTree（行为树）

- **职责**：提供基于行为树（Behavior Tree）的 AI 逻辑框架
- **描述**：实现标准的 Behavior Tree 节点结构，包含 Action（动作）、Conditional（条件）、Task（任务）等节点类型，支持组合和扩展。
- **关键类**：
  - `cn.solarmoon.spark_core.behavior_tree.Status` — 行为树节点状态枚举（Success/Failure/Running）
  - `cn.solarmoon.spark_core.behavior_tree.TreeNodeResult` — 节点执行结果
  - `cn.solarmoon.spark_core.behavior_tree.node.TreeNode` — 行为树节点接口
  - `cn.solarmoon.spark_core.behavior_tree.node.LazyTreeNode` — 延迟初始化节点
  - `cn.solarmoon.spark_core.behavior_tree.node.task.Action` — 动作节点
  - `cn.solarmoon.spark_core.behavior_tree.node.task.Conditional` — 条件节点
  - `cn.solarmoon.spark_core.behavior_tree.node.task.Task` — 任务节点

---

### EntryBuilder（注册构建器）

- **职责**：提供流式 API 简化 Minecraft 注册对象的创建流程
- **描述**：通过 Builder 模式封装 Item、BlockEntityType、Fluid、SoundEvent、Attribute、AbilityType 等对象的创建和注册逻辑，降低模板代码量。
- **关键类**：
  - `cn.solarmoon.spark_core.entry_builder.RegisterBuilder` — 注册构建器基类
  - `cn.solarmoon.spark_core.entry_builder.CommonRegisterBuilder` — 通用注册构建器
  - `cn.solarmoon.spark_core.entry_builder.ObjectRegister` — 对象注册器
  - `cn.solarmoon.spark_core.entry_builder.common.ItemBuilder` — 物品构建器
  - `cn.solarmoon.spark_core.entry_builder.common.BlockEntityTypeBuilder` — 方块实体类型构建器
  - `cn.solarmoon.spark_core.entry_builder.common.AbilityTypeBuilder` — 技能类型构建器
  - `cn.solarmoon.spark_core.entry_builder.common.AttachmentBuilder` — 附件构建器
  - `cn.solarmoon.spark_core.entry_builder.common.fluid.FluidBuilder` — 流体构建器
  - `cn.solarmoon.spark_core.entry_builder.common.AttributeBuilder` — 属性构建器

---

### BedrockModelAnimationSystem（基岩版模型动画系统）

- **职责**：完整实现 Minecraft 基岩版（Bedrock Edition）几何模型、骨骼动画和动画控制器的加载、解析与渲染管线
- **描述**：系统完整覆盖了从 JSON 解析到 GPU 渲染的全流程。使用内容包系统加载基岩版格式的几何模型（`minecraft:geometry`）、骨骼动画（`animation_controllers`）和动画数据。模型以 `OModel → OBone → OCube/OMesh` 的树形结构组织，支持 Cube（方块）和 PolyMesh（三角网格）两种几何类型。动画系统通过 `AnimController` 管理多层动画混合（Override/Additive）、过渡和权重控制，驱动骨骼姿态计算。渲染层基于 `IGeoRenderer` 体系，支持 Entity、Item、BlockEntity 的自定义渲染，以及 RenderLayer 叠加渲染。还通过 Mixin 修改原版渲染管线以实现第一人称动画、盔甲模型适配等。
- **关键类**：
  - `cn.solarmoon.spark_core.animation.IAnimatable` — 动画体接口，定义可动画对象的核心行为
  - `cn.solarmoon.spark_core.animation.IEntityAnimatable` — 实体动画体接口
  - `cn.solarmoon.spark_core.animation.ItemAnimatable` — 物品动画体实现
  - `cn.solarmoon.spark_core.animation.ICustomModelItem` — 自定义模型物品接口，使物品可使用基岩版模型
  - `cn.solarmoon.spark_core.animation.model.ModelIndex` — 模型索引（类型 + 路径），定位模型资源
  - `cn.solarmoon.spark_core.animation.model.ModelController` — 模型控制器，管理模型实例切换和纹理
  - `cn.solarmoon.spark_core.animation.model.ModelInstance` — 模型实例，绑定动画体和模型索引
  - `cn.solarmoon.spark_core.animation.model.ModelPose` — 模型姿态，管理所有骨骼的实时姿态数据
  - `cn.solarmoon.spark_core.animation.model.BonePose` — 骨骼姿态，存储单根骨骼的变换矩阵
  - `cn.solarmoon.spark_core.animation.model.origin.OModel` — 原始模型数据（纹理尺寸 + 骨骼映射）
  - `cn.solarmoon.spark_core.animation.model.origin.OBone` — 骨骼定义（轴心/旋转/子骨骼/Cube列表/网格数据/Locator）
  - `cn.solarmoon.spark_core.animation.model.origin.OCube` — 方块几何定义（UV/尺寸/膨胀/镜像），含 GPU 渲染逻辑（面剔除/顶点变换）
  - `cn.solarmoon.spark_core.animation.model.origin.OMesh` — 三角网格数据，支持 poly_mesh 格式
  - `cn.solarmoon.spark_core.animation.model.origin.OLocator` — 定位器（Locator），依附于骨骼的参考点
  - `cn.solarmoon.spark_core.animation.model.origin.OUVUnion` — UV 定义（单个 UV / 多面 UV）
  - `cn.solarmoon.spark_core.animation.anim.AnimController` — 动画控制器，管理动画层/混合/状态机 tick
  - `cn.solarmoon.spark_core.animation.anim.AnimLayer` — 动画层，支持 Override/Additive 混合模式
  - `cn.solarmoon.spark_core.animation.anim.AnimInstance` — 动画实例，管理单个动画的播放状态和骨骼关键帧
  - `cn.solarmoon.spark_core.animation.anim.AnimEvent` — 动画事件（Start/Interrupted/Completed/End/Tick/Notify）
  - `cn.solarmoon.spark_core.animation.anim.BlendMode` — 混合模式枚举（OVERRIDE / ADDITIVE）
  - `cn.solarmoon.spark_core.animation.anim.KeyAnimData` — 关键帧动画数据（位置/旋转/缩放）
  - `cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet` — 动画集合，一个模型的所有动画
  - `cn.solarmoon.spark_core.animation.anim.origin.OAnimation` — 单个动画定义，包含骨骼变换关键帧
  - `cn.solarmoon.spark_core.animation.anim.origin.OBoneAnimation` — 骨骼动画，单个骨骼的关键帧序列
  - `cn.solarmoon.spark_core.animation.anim.origin.AnimIndex` — 动画索引（模型索引 + 动画名称）
  - `cn.solarmoon.spark_core.animation.anim.AnimInstanceBuilder` — 动画实例构建工具函数
  - `cn.solarmoon.spark_core.animation.anim.AnimApplier` — 动画应用器，注册事件驱动动画 tick
  - `cn.solarmoon.spark_core.animation.state.AnimStateMachine` — 动画状态机，驱动动画状态切换
  - `cn.solarmoon.spark_core.animation.state.origin.OAnimStateMachineSet` — 动画控制器集合
  - `cn.solarmoon.spark_core.animation.state.origin.OAnimStateMachine` — 动画控制器定义
  - `cn.solarmoon.spark_core.animation.AnimationStateDefinition` — 动画状态定义（动画 + 混合空间 + 过渡）
  - `cn.solarmoon.spark_core.animation.renderer.IGeoRenderer` — 几何渲染器接口，定义渲染管线
  - `cn.solarmoon.spark_core.animation.renderer.GeoLivingEntityRenderer` — 实体几何渲染器
  - `cn.solarmoon.spark_core.animation.renderer.GeoItemRenderer` — 物品几何渲染器
  - `cn.solarmoon.spark_core.animation.renderer.ModelRenderHelper` — 模型渲染辅助函数
  - `cn.solarmoon.spark_core.animation.renderer.layer.RenderLayer` — 附加渲染层抽象类
  - `cn.solarmoon.spark_core.mixin.animation.*` — 注入原版渲染管线的 Mixin（第一人称/盔甲/模型部件等）

---

### CompatibilityLayer（兼容层）

- **职责**：提供与其他知名 Mod 的兼容支持
- **描述**：通过 Mixin 和适配器模式，支持 Create（机械动力）、GeckoLib、AzureLib、Sodium（铟）、PlayerAnimator、FirstPersonModel、RealCamera 等 Mod 的兼容。
- **关键类**：
  - `cn.solarmoon.spark_core.compat.CompatDetector` — Mod 兼容性检测器
  - `cn.solarmoon.spark_core.compat.create.CreateCompat` — Create Mod 兼容入口
  - `cn.solarmoon.spark_core.compat.create.CreateContraptionPhysicsHost` — Create 动力的物理宿主
  - `cn.solarmoon.spark_core.compat.create.CreateContraptionShapeBuilder` — Create 碰撞形状构建器
  - `cn.solarmoon.spark_core.compat.sodium.SodiumCompat` — Sodium 兼容
  - `cn.solarmoon.spark_core.compat.player_animator.PlayerAnimatorCompat` — PlayerAnimator 兼容

---

### JavaScriptEngine（JavaScript 引擎）

- **职责**：在 Minecraft Mod 中集成 JavaScript 脚本能力
- **描述**：基于 OpenJDK Nashorn/GraalVM 等引擎，提供 JS 运行时环境，支持 MoLang 表达式求值、脚本化动画查询等。
- **关键类**：
  - `cn.solarmoon.spark_core.js.JavaScript` — JS 引擎核心
  - `cn.solarmoon.spark_core.js.molang.IMolangContext` — MoLang 上下文接口
  - `cn.solarmoon.spark_core.js.molang.QueryContext` — MoLang 查询上下文
  - `cn.solarmoon.spark_core.js.molang.VariableContext` — 变量上下文
  - `cn.solarmoon.spark_core.js.molang.MathContext` — 数学函数上下文

---

### TaskSubmitOffice（任务提交办公室）

- **职责**：提供分阶段的任务调度机制
- **描述**：允许在 GameTick 的特定阶段（Phase）提交和延迟执行任务，支持去重和即时任务两种模式。PhysicsLevel 和 LevelPatch 都实现了此接口。
- **关键类**：
  - `cn.solarmoon.spark_core.util.TaskSubmitOffice` — 任务提交办公室接口
  - `cn.solarmoon.spark_core.util.PPhase` — 任务阶段枚举，定义任务在哪个 tick 阶段执行

---

### SoundEffectUtils（音效工具）

- **职责**：提供音效处理和生成工具
- **描述**：包含波形生成器、音频滤波器（卷积滤波、抖动滤波、单声道滤波），可对游戏音效进行实时处理和效果增强。
- **关键类**：
  - `cn.solarmoon.spark_core.util.sound.SoundHelper` — 音效辅助工具
  - `cn.solarmoon.spark_core.util.sound.WaveGenerators` — 波形生成器
  - `cn.solarmoon.spark_core.util.sound.WaveEffects` — 波形效果器
  - `cn.solarmoon.spark_core.util.sound.filter.ConvolutionFilter` — 卷积滤波器
  - `cn.solarmoon.spark_core.util.sound.filter.MonoFilter` — 单声道滤波器
  - `cn.solarmoon.spark_core.util.sound.filter.JitterFilter` — 抖动滤波器
