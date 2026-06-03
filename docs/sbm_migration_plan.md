# Spark-Core 模型动画系统 → SimpleBedrockModel 迁移计划 (v2)

> **状态**: 草案 · **创建**: 2026-06-04 · **修订**: 2026-06-04（根据审阅意见全面修正）  
> **目标版本**: Spark-Core 1.x

---

## 目录

1. [目标与范围](#1-目标与范围)
2. [当前架构概览](#2-当前架构概览)
3. [目标架构概览](#3-目标架构概览)
4. [核心概念映射表](#4-核心概念映射表)
5. [逐步实施计划](#5-逐步实施计划)
   - [Phase 1: 依赖与全局初始化](#phase-1-依赖与全局初始化)
   - [Phase 2: 模型数据层](#phase-2-模型数据层)
   - [Phase 3: 模型运行时层](#phase-3-模型运行时层)
   - [Phase 4: 动画系统](#phase-4-动画系统)
   - [Phase 5: 渲染管线](#phase-5-渲染管线)
   - [Phase 6: Molang 系统](#phase-6-molang-系统)
   - [Phase 7: 内容包对接](#phase-7-内容包对接)
   - [Phase 8: Machine-Max 适配](#phase-8-machine-max-适配)
   - [Phase 9: 渐进式删除旧代码](#phase-9-渐进式删除旧代码)
6. [不变部分清单](#6-不变部分清单)
7. [删除清单（分阶段）](#7-删除清单分阶段)
8. [风险与缓解](#8-风险与缓解)
9. [关键代码示例](#9-关键代码示例)
   - [9.1 全局 MolangEngine 初始化](#91-全局-molangengine-初始化)
   - [9.2 MolangContext 子类（自定义查询）](#92-molangcontext-子类自定义查询)
   - [9.3 内容包 → BakedBedrockModel 直接构建](#93-内容包--bakedbedrockmodel-直接构建)
   - [9.4 IAnimatable 实现（SubPart 示例）](#94-ianimatable-实现subpart-示例)
   - [9.5 SBMSimpleAnimator（AnimationRunner 封装）](#95-sbmsimpleanimatoranimationrunner-封装)
   - [9.6 SBMModelController（含 BoneState 插值缓存）](#96-sbmmodelcontroller含-bonestate-插值缓存)
   - [9.7 PartEntityRenderer（NeoForge 1.21.1 EntityRenderState）](#97-partentityrendererneoforge-1211-entityrenderstate)

---

## 1. 目标与范围

### 目标

将 Spark-Core 的自研模型加载、渲染和动画系统替换为 [SimpleBedrockModel](https://github.com/McModderAnchor/SimpleBedrockModel)（以下简称 SBM）v2 API，作为 **SBM Adapter 层**存在于 Spark-Core 内部。

### 范围

| 替换 | 不变 |
|------|------|
| 模型数据结构（OModel/OBone/OCube/OMesh）→ SBM v2 `BakedBedrockModel` | 物理系统（Bullet/JME/SparkMathKt 全保留） |
| 模型运行时（ModelInstance/ModelPose/BonePose）→ `BedrockModelInstance` + `BoneState` | 内容包管线（SparkPackLoader/MMDynamicRes 全保留） |
| 动画数据与播放（OAnimation/OAnimationSet/OKeyFrame）→ MAE `AnimationRunner` | `IAnimatable` 接口签名（保留 `ModelController` 返回类型） |
| 渲染管线（GeoEntityRenderer/ModelRenderHelper）→ `BakedBedrockModel.renderToBuffer()` | Machine-Max SubPart/VehicleCore 核心逻辑 |
| Molang 引擎（GraalVM JS）→ `MochaEngine` 编译 + `MolangContext` 求值 | `SubPartBinding`/`VehicleBinding` 业务逻辑（改为 `MolangContext` 子类） |
| 粒子系统 → SBM 粒子系统 | 所有 subsystem/connector/recipe 逻辑 |
| 模型注册表（OModel.ORIGINS）→ Spark-Core 自维护 `BakedBedrockModel` 注册表 | GraalVM JS 保留为通用脚本引擎 |

### 不在此范围

- 分层动画（AnimController/AnimLayer）—— Machine-Max 确认暂不需要
- 动画状态机（AnimStateMachine）—— 暂不需要
- 动画生命周期过渡（ENTER→WORK→EXIT 状态机）—— 暂不需要

### 核心设计决策

**不经过 SBM 资源加载系统**。SBM v2 的 `BedrockModelResources` 使用 Minecraft `ResourceManager.getResource()` 加载文件，路径固定为 `assets/{namespace}/models/bedrock/{path}.json`，与 `spark_modules/` 路径不兼容。替代方案：直接调用 SBM 的 **public static 工厂方法**—— `BakedBedrockModel.bake(pojo, options)` 和 `BedrockAnimation.createAnimation(file, indexProvider)` 从 SparkPackLoader 获取 JSON 流后直接构建，维护自己的注册表。

---

## 2. 当前架构概览

```
IAnimatable<T>                           // 动画体接口（保留，不改签名）
├── animController: AnimController       // 动画分层混合器（删除）
│     ├── layers: Map<Int, AnimLayer>    // 动画层（删除）
│     └── blendBone(bone): KeyAnimData  // 骨骼级混合（重构）
├── modelController: ModelController     // 模型控制器（重构，接口保留）
│     └── model: ModelInstance           // 模型实例（替换）
│           ├── index: ModelIndex        // (type, ResourceLocation)
│           ├── origin: OModel           // ← OModel.ORIGINS 静态注册表
│           │     └── bones: Map<String, OBone>
│           └── pose: ModelPose          // 骨骼运行时位姿
└── variables: MutableMap<String, Any>

Molang: JSMolangValue(String)            // GraalVM JS 引擎执行
```

### 当前数据流

```
spark_modules/*.json
  → SparkPackLoader → ModelModule.read()
    → 坐标变换 → OModel.ORIGINS[ModelIndex] = OModel(bones, cubes, mesh)

运行时:
  SubPart(IAnimatable)
    → modelController.setModel(ModelIndex("part", location))
    → animController.playAnimation(AnimInstance(origin=OAnimation))
    → PartEntityRenderer.render() → OBone.applyTransformWithParents(pose) → OCube.renderVertexes()
```

---

## 3. 目标架构概览

```
IAnimatable<T>                         // 接口保留，签名不变
├── modelController: ModelController   // ← 返回类型不变
│     └── (内部持有) SBMModelController
│           ├── modelIndex: ModelIndex
│           ├── bakedModel: BakedBedrockModel    // 不可变
│           ├── instance: BedrockModelInstance   // 运行时 BoneState[]
│           └── prevBoneStates: BoneState[]      // 插值缓存
├── (新增) getSimpleAnimator()          // 简化动画器
└── variables: MutableMap<String, Any>

全局单例:
  MochaEngine<?> MOLANG_ENGINE          // 编译时使用，仅一个实例

Molang 求值（per-frame, per-instance）:
  MechMolangContext(SubPart)            // 继承 MolangContext<SubPart>
    → @QueryBinding 方法提供 query.xxx 绑定
    → ctx.prepareEvaluation(timeS) → expr.evaluate(ctx)

渲染: PartEntityRenderer               // 直接继承 EntityRenderer（处理 EntityRenderState）
        → instance.renderToBuffer(poseStack, bufferSource, quadRT, triRT, light, overlay)

动画: AnimationRunner                   // MAE 库
        = new AnimationRunner(anim, new AnimationContext(lengthS))
        → runner.setState(new LoopingState(clock))
        → runner.tick() → Pose = runner.evaluate()
        → instance.applyPose(pose)
```

### 目标数据流

```
// 模型加载（SparkPackLoader → 直接调用 SBM 静态方法）
BedrockModelPOJO pojo = GsonUtil.CLIENT_GSON.fromJson(reader, BedrockModelPOJO.class);
BakedBedrockModel baked = BakedBedrockModel.bake(pojo, BakerOptions.ofAnimatedBones(allBoneNames));
// 存入 Spark-Core 自己的注册表
modelRegistry[modelIndex] = baked;

// 动画加载
BedrockAnimationFile file = GsonUtil.CLIENT_GSON.fromJson(reader, BedrockAnimationFile.class);
List<BedrockAnimation> animations = BedrockAnimation.createAnimation(file, baked, MOLANG_ENGINE);

// 运行时
SubPart(IAnimatable)
  → modelController = SBMModelController(ModelIndex("part", location))
  → bakedModel = modelRegistry[modelIndex]
  → instance = bakedModel.createInstance()
  → runner = new AnimationRunner(animation, new AnimationContext(length))
  → runner.setState(new LoopingState(clock))
  → runner.tick() → instance.applyPose(runner.evaluate())
  → PartEntityRenderer.render() → instance.renderToBuffer(...)
```

---

## 4. 核心概念映射表

| 层面 | Spark-Core（当前） | SBM v2（目标） |
|------|-------------------|----------------|
| **模型注册** | `OModel.ORIGINS: Map<ModelIndex, OModel>` | Spark-Core 自维护 `Map<ModelIndex, BakedBedrockModel>` |
| **模型构建** | 自解析 JSON + 坐标变换 | `BakedBedrockModel.bake(pojo, options)` |
| **模型定义** | `OModel` (含骨骼/cube/mesh) | `BakedBedrockModel` (BoneDefinition[] + BakedGeometryChunk[]) |
| **骨骼定义** | `OBone` (含 pivot/cubes/mesh) | `BoneDefinition` (record, 无几何体) |
| **几何体** | `OCube`/`OMesh` (OBone 内部) | `BakedGeometryChunk` (扁平数组) |
| **定位器** | `OLocator` | `BoneLocator` (record) |
| **退化骨骼** | 无 | `QueryTransform` (record) |
| **模型运行时** | `ModelInstance` → `ModelPose` → `BonePose` | `BedrockModelInstance` → `BoneState[]` |
| **骨骼运行时** | `BonePose` (双缓冲) | `BoneState` (public x/y/z/rotation/scale) + **适配层 lerp 缓存** |
| **模型控制器** | `ModelController` | `SBMModelController`（包装，实现 `ModelController` 相同的方法签名） |
| **骨骼全局变换** | `BonePose.getSpaceBoneMatrix(pt)` | `BedrockModelInstance.getGlobalTransform(index)` |
| **定位器变换** | `ModelPose.getWorldBoneLocatorMatrix(name, pt)` | `BedrockModelInstance.getLocatorTransform(name)` |
| **动画构建** | `AnimationModule` 自解析 | `BedrockAnimation.createAnimation(file, indexProvider, engine)` |
| **动画数据** | `OAnimation`/`OBoneAnimation`/`OKeyFrame` | `BedrockAnimation` (MAE `BasicAnimation`) |
| **动画播放** | `AnimInstance` + `AnimController.physTick()` | `AnimationRunner` + `LoopingState`/`PlayingState` |
| **动画采样** | `blendBone()` 逐骨骼插值 | `runner.evaluate()` → `Pose` → `instance.applyPose(pose)` |
| **动画混合** | `AnimController.blendBone()` (自研) | `EulerAdditiveBlender` (MAE) |
| **Molang 引擎** | GraalVM JS | `MochaEngine<?>` 全局单例（编译用） |
| **Molang 求值** | `JSMolangValue.eval(anim)` → `Value` | `MolangExpression.evaluate(MolangContext)` → `double` |
| **Molang 绑定** | `IMolangContext` + `MolangRegisterEvent` | `@QueryBinding` 在 `MolangContext` 子类上 |
| **Molang 变量** | `IAnimatable.variables` | `MutableObjectBinding` (per-context) |
| **渲染器基类** | `GeoEntityRenderer` | `EntityRenderer<MMPartEntity, EntityRenderState>` (原版) |
| **渲染调用** | `ModelRenderHelperKt.render(OBone, pose, ...)` | `BakedBedrockModel.renderToBuffer(instance, ...)` |
| **Skeleton 接口** | `BoneIndexProvider` | `BedrockModelInstance` 已实现 `Skeleton` |
| **模型索引** | `ModelIndex(type, location)` | 保留，type 编码到 `location.getPath()` 避免冲突 |
| **内容包模型加载** | `ModelModule` → `OModel.ORIGINS` | 自定义 `SparkToSBMBridge` → `modelRegistry` |
| **资源重载** | `SparkPackLoader` 内部 | `SimplePreparableReloadListener` (SBM 模式，桥接到 SparkPackLoader) |

---

## 5. 逐步实施计划

### Phase 1: 依赖与全局初始化

**目标**: 添加 SBM 依赖，创建全局单例组件。

#### 1.1 添加依赖

```groovy
// build.gradle
dependencies {
    // SBM v2
    implementation "com.github.mcmodderanchor:simplebedrockmodel-neoforge:${sbm_version}"
    
    // GraalVM JS — 保留作为通用脚本引擎（未来 UGC）
    runtimeOnly "org.graalvm.polyglot:js-community:..."
}
```

#### 1.2 全局 MochaEngine 单例

新建 `cn/solarmoon/spark_core/animation/molang/MMMolangEngine.kt`：

```kotlin
object MMMolangEngine {
    /** 全局 Molang 引擎（只用于编译，不存储 per-instance 状态） */
    val ENGINE: MochaEngine<*> = MochaEngine.create(null) { builder ->
        builder.set("math", JavaObjectBinding.of(MochaMath::class.java, null, MochaMath()))
        // variable / v 绑定在 context 创建时添加
    }
    
    /** 编译 Molang 表达式为可复用的 MolangExpression */
    fun compile(expression: String): MolangExpression {
        return ENGINE.compile(expression, MolangExpression::class.java)
    }
}
```

#### 1.3 模型注册表

新建 `cn/solarmoon/spark_core/animation/model/ModelRegistry.kt`：

```kotlin
object ModelRegistry {
    private val models = ConcurrentHashMap<ModelIndex, BakedBedrockModel>()
    private val animations = ConcurrentHashMap<ModelIndex, Map<ResourceLocation, List<BedrockAnimation>>>()
    
    fun register(index: ModelIndex, model: BakedBedrockModel) { models[index] = model }
    fun get(index: ModelIndex): BakedBedrockModel? = models[index]
    
    fun registerAnimations(modelIndex: ModelIndex, anims: Map<ResourceLocation, List<BedrockAnimation>>) {
        animations[modelIndex] = anims
    }
    fun getAnimations(modelIndex: ModelIndex): Map<ResourceLocation, List<BedrockAnimation>>? {
        return animations[modelIndex]
    }
}
```

**ModelIndex 与 ResourceLocation 的冲突处理**：`ModelIndex(type, location)` 中 type 可能为 `"part"`/`"item"`/`"block"`。由于 `BakedBedrockModel` 是统一的，不同 type 可共享同一模型数据，因此注册表以 `ResourceLocation` 为 key，`type` 仅用于索引查找。

```kotlin
// 将 type 编码到 path 前缀以区分同名模型的不同 type
fun ModelIndex.toQualifiedLocation(): ResourceLocation {
    val (type, loc) = this
    return ResourceLocation(loc.namespace, "$type/${loc.path}")
}
```

**产出**:
- [ ] `build.gradle` 添加 SBM 依赖，GraalVM 保留
- [ ] `MMMolangEngine.kt` 全局单例
- [ ] `ModelRegistry.kt` 自维护注册表

---

### Phase 2: 模型数据层

**目标**: 通过 SparkPackLoader 加载 JSON，直接用 SBM 静态方法构建 `BakedBedrockModel`。

#### 2.1 桥接器

新建 `cn/solarmoon/spark_core/animation/model/SparkToSBMBridge.kt`：

```kotlin
object SparkToSBMBridge {
    /**
     * 从 SparkPack 的 JSON 流构建 BakedBedrockModel。
     * 完全绕过 SBM 的 ResourceManager 加载机制。
     */
    fun loadModel(reader: Reader): BakedBedrockModel {
        val pojo = GsonUtil.CLIENT_GSON.fromJson(reader, BedrockModelPOJO::class.java)
        
        // 收集所有骨骼名作为动态骨骼
        val boneNames = collectAllBoneNames(pojo)
        val options = BakerOptions.ofAnimatedBones(boneNames)
        
        return BakedBedrockModel.bake(pojo, options)
    }
    
    /**
     * 从 SparkPack 的 JSON 流构建 BedrockAnimation 列表。
     */
    fun loadAnimations(reader: Reader, model: BakedBedrockModel): List<BedrockAnimation> {
        val file = GsonUtil.CLIENT_GSON.fromJson(reader, BedrockAnimationFile::class.java)
        return BedrockAnimation.createAnimation(file, model, MMMolangEngine.ENGINE)
    }
    
    private fun collectAllBoneNames(pojo: BedrockModelPOJO): Set<String> {
        val names = mutableSetOf<String>()
        // 从 geometry 中提取所有骨骼名
        for (geometry in pojo.getGeometry()) {
            for (bone in geometry.getBones()) {
                names.add(bone.getName())
            }
        }
        return names
    }
}
```

**关键点**: 不需要 `RegisterV2BedrockResourcesEvent` 事件！SBM 的 `BakedBedrockModel.bake()` 和 `BedrockAnimation.createAnimation()` 都是 public static 方法，不需要经过 SBM 的资源注册管线。Spark-Core 自己的 `ModelRegistry` 存储结果。

#### 2.2 修改 ModelModule

在 `ModelModule.read()` 中，将旧的 `OModel.ORIGINS[ModelIndex] = ...` 替换为：

```kotlin
val baked = SparkToSBMBridge.loadModel(reader)
ModelRegistry.register(modelIndex, baked)
```

动画同理，在 `AnimationModule.read()` 中替换为 `SparkToSBMBridge.loadAnimations(reader, model)`。

**产出**:
- [ ] `SparkToSBMBridge.kt` 桥接器
- [ ] 修改 `ModelModule.read()`
- [ ] 修改 `AnimationModule.read()`

---

### Phase 3: 模型运行时层

**目标**: 创建 `SBMModelController` 包装 `BakedBedrockModel` + `BedrockModelInstance`，含 BoneState 插值缓存。

#### 3.1 SBMModelController

新建 `cn/solarmoon/spark_core/animation/model/SBMModelController.kt`：

```kotlin
class SBMModelController(val animatable: IAnimatable<*>) {
    
    var modelIndex: ModelIndex = animatable.defaultModelIndex
        private set
    
    /** SBM 烘焙模型（不可变） */
    var bakedModel: BakedBedrockModel? = null
        private set
    
    /** SBM 运行时实例（可变 BoneState[]） */
    var instance: BedrockModelInstance? = null
        private set
    
    /** 纹理路径 */
    var textureLocation: ResourceLocation? = null
    
    // === BoneState 插值缓存（解决 SBM BoneState 无双缓冲问题） ===
    private var prevBoneX: FloatArray? = null
    private var prevBoneY: FloatArray? = null
    private var prevBoneZ: FloatArray? = null
    private var prevBoneRotX: FloatArray? = null
    private var prevBoneRotY: FloatArray? = null
    private var prevBoneRotZ: FloatArray? = null
    private var prevBoneRotW: FloatArray? = null
    
    fun setModel(index: ModelIndex?) {
        if (index == null) return
        modelIndex = index
        bakedModel = ModelRegistry.get(index)
        instance = bakedModel?.createInstance()
        instance?.resetPose()
        initInterpCache()
    }
    
    fun setTextureLocation(location: ResourceLocation) {
        textureLocation = location
    }
    
    /**
     * 在 applyPose 前调用，将当前 BoneState 保存为上一帧值（用于插值）。
     *
     * 调用时序：
     *   [物理 tick N]   snapshot() → 保存 pose_{N-1} → applyPose(pose_N)
     *   [物理 tick N+1] snapshot() → 保存 pose_N     → applyPose(pose_{N+1})
     *   [渲染帧]        lerp(snapshot(pose_N), current(pose_{N+1}), partialTick)
     */
    fun snapshotBoneStates() {
        val inst = instance ?: return
        val bones = inst.boneIndexes
        val count = bones.size
        if (prevBoneX == null || prevBoneX!!.size != count) initInterpCache()
        for (i in 0 until count) {
            val b = bones[i]
            prevBoneX!![i] = b.x
            prevBoneY!![i] = b.y
            prevBoneZ!![i] = b.z
            prevBoneRotX!![i] = b.rotation.x()
            prevBoneRotY!![i] = b.rotation.y()
            prevBoneRotZ!![i] = b.rotation.z()
            prevBoneRotW!![i] = b.rotation.w()
        }
    }
    
    /** 对指定骨骼做插值（physics tick 20Hz → render 60Hz+ 平滑）。
     *  调用前确保 snapshotBoneStates() + applyPose() 已完成。
     *  prevBone* 保存的是上一物理帧的位姿，current BoneState 是本物理帧的位姿。
     */
    fun lerpBoneState(boneIndex: Int, partialTick: Float): BoneState {
        val bones = instance!!.boneIndexes
        val current = bones[boneIndex]
        if (prevBoneX == null) return current  // 首帧无历史数据
        
        val lerped = BoneState(current.definition())
        lerped.x = lerp(prevBoneX!![boneIndex], current.x, partialTick)
        lerped.y = lerp(prevBoneY!![boneIndex], current.y, partialTick)
        lerped.z = lerp(prevBoneZ!![boneIndex], current.z, partialTick)
        lerped.rotation.set(prevBoneRotX!![boneIndex], prevBoneRotY!![boneIndex], 
                            prevBoneRotZ!![boneIndex], prevBoneRotW!![boneIndex])
            .slerp(current.rotation, partialTick)
        lerped.xScale = lerp(1f, current.xScale, partialTick)
        lerped.yScale = lerp(1f, current.yScale, partialTick)
        lerped.zScale = lerp(1f, current.zScale, partialTick)
        lerped.visible = current.visible
        return lerped
    }
    
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    
    private fun initInterpCache() {
        val count = instance?.boneIndexes?.size ?: 0
        prevBoneX = FloatArray(count)
        prevBoneY = FloatArray(count)
        prevBoneZ = FloatArray(count)
        prevBoneRotX = FloatArray(count)
        prevBoneRotY = FloatArray(count)
        prevBoneRotZ = FloatArray(count)
        prevBoneRotW = FloatArray(count)
    }
    
    val boneDefinitions: List<BoneDefinition>
        get() = bakedModel?.bones()?.toList() ?: emptyList()
    
    fun getLocatorTransform(name: String): Matrix4f? = instance?.getLocatorTransform(name)
    
    fun getGlobalTransform(boneIndex: Int): Matrix4f? = instance?.getGlobalTransform(boneIndex)
}
```

#### 3.2 IAnimatable 接口策略

**最终方案**: `IAnimatable` 接口中 `getModelController()` 返回类型保持为 `ModelController`（旧类）。实现类内部持有 `SBMModelController`，在 `getModelController()` 中通过向下转型或新建一个薄 `ModelController` 包装返回。

Machine-Max 代码中所有调用 `getModelController()` 获取 `ModelController` 的地方，如果后续方法签名有变化，由 Spark-Core 的 `ModelController` 类内部做兼容转发。

**产出**:
- [ ] `SBMModelController.kt`（含 BoneState 插值缓存）
- [ ] `ModelController` 旧类改为兼容包装，内部委托 `SBMModelController`

---

### Phase 4: 动画系统

**目标**: 用 MAE `AnimationRunner` 替代自研 `AnimController`/`AnimInstance`。

#### 4.1 正确 API

```java
// 来源: ZtiAnimationContext.java, TestBlockAnimationContext.java
import com.maydaymemory.mae.control.runner.AnimationRunner;
import com.maydaymemory.mae.control.runner.AnimationContext;
import com.maydaymemory.mae.control.runner.LoopingState;
import com.maydaymemory.mae.control.runner.PlayingState;
import com.maydaymemory.mae.control.runner.StopState;

// 创建
AnimationRunner runner = new AnimationRunner(animation, new AnimationContext(animation.getSpecifiedEndTimeS()));

// 循环播放
runner.setState(new LoopingState(clock));

// 单次播放
runner.setState(new PlayingState(clock, StopState::new));

// 驱动
runner.tick();

// 获取当前 Pose
Pose pose = runner.evaluate();

// 获取进度（秒）
float time = runner.getProgressInSecond();
```

#### 4.2 SBMSimpleAnimator

```kotlin
class SBMSimpleAnimator(private val controller: SBMModelController) {
    
    private var runner: AnimationRunner? = null
    private val blender = EulerAdditiveBlender()
    
    fun playLooping(animation: BedrockAnimation, clock: AnimationClock) {
        runner = AnimationRunner(animation, AnimationContext(animation.specifiedEndTimeS))
        runner?.setState(LoopingState(clock))
    }
    
    fun playOnce(animation: BedrockAnimation, clock: AnimationClock) {
        runner = AnimationRunner(animation, AnimationContext(animation.specifiedEndTimeS))
        runner?.setState(PlayingState(clock, StopState::new))
    }
    
    fun tick() {
        runner?.tick()
    }
    
    /** 获取混合后的 Pose（bindPose + 动画 Pose） */
    fun evaluateBlended(): Pose? {
        val animPose = runner?.evaluate() ?: return null
        val bindPose = controller.instance?.bindPose ?: return null
        return blender.blend(bindPose, animPose)
    }
    
    val isPlaying: Boolean get() = runner != null
}
```

#### 4.3 SubPart.postTick 改造

```java
// 当前
if (!animController.isPlayingAnim() && animSet != null) {
    for (entry : animSet.getAnimations().entrySet()) {
        new AnimInstance(this, new AnimIndex(modelIndex, entry.getKey())).enter();
    }
}

// 迁移后
var animator = getSimpleAnimator();
if (!animator.isPlaying() && defaultAnim != null) {
    animator.playLooping(defaultAnim, clock);
}
```

**产出**:
- [ ] `SBMSimpleAnimator.kt`
- [ ] 修改 `SubPart.postTick`
- [ ] 修改 `FabricatorBlockEntity`

---

### Phase 5: 渲染管线

**目标**: 用 SBM `renderToBuffer()` 替代自研渲染，处理 NeoForge 1.21.1 EntityRenderState。

#### 5.1 核心变更

NeoForge 1.21.1 引入了 `EntityRenderer<T, S extends EntityRenderState>` 新签名。PartEntityRenderer 不再继承 `GeoEntityRenderer`，直接继承 `EntityRenderer` 并处理 `EntityRenderState`。

#### 5.2 需获取的关键信息

当前 `PartEntityRenderer` 中通过 `entity.subPart` 直接访问 `SubPart`。在 1.21.1 渲染系统中，`render(state, poseStack, bufferSource, packedLight)` 的 `state` 是 `EntityRenderState`，entity 通过 `state` 间接获取。

需要在 `extractRenderState()` 中将 entity 引用存入自定义 RenderState 子类。

**产出**:
- [ ] `PartEntityRenderer` 重构（详见 [9.7](#97-partentityrendererneoforge-1211-entityrenderstate)）
- [ ] `FabricatorBlockEntityRenderer`/`ResearchTableBlockEntityRenderer`/`TotalStationBlockEntityRenderer` 重构
- [ ] `CustomModelItemRenderer`/`BlockEntityItemRenderer` 重构

---

### Phase 6: Molang 系统

**目标**: 全局 `MochaEngine` 单例 + per-instance `MolangContext` 子类。

#### 6.1 架构

```
全局单例（Spark-Core init 时创建一次）:
  MMMolangEngine.ENGINE                       // 编译用
    → compileExpression("query.durability")   // → MolangExpression (可复用)

编译阶段（动画加载时）:
  BedrockAnimation.createAnimation(file, model, MMMolangEngine.ENGINE)
  // 引擎将动画中的 Molang 表达式编译为 MolangExpression

求值阶段（运行时，per-instance）:
  MechMolangContext ctx = new MechMolangContext(subPart);
  ctx.prepareEvaluation(animationTime);
  double value = compiledExpr.evaluate(ctx);
```

#### 6.2 MolangContext 子类

新建 `cn/solarmoon/spark_core/animation/molang/MechMolangContext.kt`：

```kotlin
/**
 * 所有 Machine-Max 自定义查询的 MolangContext。
 * 替代 SubPartBinding + VehicleBinding。
 * 线程安全：per-frame 创建新实例，无竞争。
 */
class MechMolangContext(subPart: SubPart?) : MolangContext<SubPart>(subPart) {
    
    // 无参构造（供 MolangEngineHelper.createEngine(Class) 反射）
    constructor() : this(null)
    
    // === query.xxx (默认命名空间) ===
    
    @QueryBinding("anim_time")  // 覆盖父类的同名方法
    override fun queryAnimTime(): Double = super.queryAnimTime()
    
    // === subpart.xxx ===
    
    @QueryBinding("durability", namespace = "subpart")
    fun subpartDurability(): Double {
        val e = entity ?: return 0.0
        return e.durability
    }
    
    @QueryBinding("max_durability", namespace = "subpart")
    fun subpartMaxDurability(): Double {
        val e = entity ?: return 0.0
        return e.maxDurability
    }
    
    @QueryBinding("is_destroyed", namespace = "subpart")
    fun subpartDestroyed(): Double {
        val e = entity ?: return 0.0
        return if (e.isDestroyed) 1.0 else 0.0
    }
    
    @QueryBinding("has_connector", namespace = "subpart")
    fun hasConnector(name: String): Double {
        val e = entity ?: return 0.0
        // 注意：带参数的 Molang 调用需要检查 MochaEngine 是否支持
        // 若不支持，改为暴露固定数量的 query
        return if (e.hasConnector(name)) 1.0 else 0.0
    }
    
    // === veh.xxx ===
    
    @QueryBinding("durability", namespace = "veh")
    fun vehDurability(): Double {
        val core = entity?.vehicleCore ?: return 0.0
        return core.durability
    }
    
    @QueryBinding("energy", namespace = "veh")
    fun vehEnergy(): Double {
        val core = entity?.vehicleCore ?: return 0.0
        return core.energyGrid.totalEnergy
    }
    
    // ... 其他查询
}
```

#### 6.3 替换 JSMolangValue

```java
// 旧
var result = JSMolangValueKt.evalAsDouble("query.durability / query.max_durability", animatable);

// 新
var ctx = new MechMolangContext(subPart);
ctx.prepareEvaluation(animTime);
double result = MMMolangEngine.INSTANCE.compile("subpart.durability / subpart.max_durability").evaluate(ctx);
```

**产出**:
- [ ] `MechMolangContext`（替代 `SubPartBinding` + `VehicleBinding`）
- [ ] 删除 `IMolangContext`/`MolangRegisterEvent`/`JSMolangValue`
- [ ] 修改 HUD 文本 Molang 调用

---

### Phase 7: 内容包对接（简化版）

**目标**: `ModelModule` 和 `AnimationModule` 内部改用 `SparkToSBMBridge`。

如前所述，不经过 SBM `RegisterV2BedrockResourcesEvent`，直接在 `ModelModule.read()` 和 `AnimationModule.read()` 中调用 `SparkToSBMBridge.loadModel()` / `loadAnimations()`。

**产出**:
- [ ] 修改 `ModelModule.read()`
- [ ] 修改 `AnimationModule.read()`
- [ ] 删除 `OModel.ORIGINS` 和 `OAnimationSet.ORIGINS` 的写入代码

---

### Phase 8: Machine-Max 适配

#### 8.1 SubPart.java 改动

| 当前 | 改动 |
|------|------|
| `new ModelController(this)` | 不变，`ModelController` 改为兼容包装 |
| `new AnimController(this)` | 删除，改为 `SBMSimpleAnimator` |
| `modelController.setModel(new ModelIndex("part", location))` | 不变 |
| `animController.physTick()` | 删除 |
| `animController.isPlayingAnim()` | 改为 `getSimpleAnimator().isPlaying()` |
| `new AnimInstance(...).enter()` | 改为 `getSimpleAnimator().playLooping(anim, clock)` |

#### 8.2 IAnimatable 实现类

**策略**: 保留 `getModelController()` 返回旧 `ModelController` 类型。实现类内部持有 `SBMModelController`，新 `ModelController` 做薄包装转发。

Machine-Max 类改动量约 **20-30 行/文件**（主要是构造方法中 AnimController 的移除）。

#### 8.3 MMMolangs.java

**删除**。不再需要事件注册，改为 Spark-Core 侧初始化 `MechMolangContext` 类。

**产出**:
- [ ] `SubPart.java` ~25 行
- [ ] `PartAnimatable.java` ~5 行
- [ ] `SubPartAnimatable.java` ~5 行
- [ ] `VehicleAnimatable.java` ~5 行
- [ ] `PartEntityRenderer.java` ~80 行
- [ ] `FabricatorBlockEntityRenderer.java` ~20 行
- [ ] `CustomModelItemRenderer.java` ~20 行
- [ ] 删除 `MMMolangs.java`

---

### Phase 9: 渐进式删除旧代码

采用四阶段策略，避免中间阶段编译失败：

#### 阶段 A: 标记废弃（所有新代码到位后）
```kotlin
@Deprecated("已迁移至 SBM v2", ReplaceWith("BakedBedrockModel"))
data class OModel(...)
```

#### 阶段 B: 删除 Machine-Max 侧引用（确认编译通过）
逐个删除 Machine-Max 中对废弃类的 import 和使用。

#### 阶段 C: 删除 Spark-Core 旧实现类
`OBone`, `OCube`, `OMesh`, `OAnimation`, `AnimController`, `AnimInstance` 等。

#### 阶段 D: 清理 Old ModelController
删除旧的 `ModelController` 实现，仅保留薄包装类。

---

## 6. 不变部分清单

| 组件 | 所属项目 | 说明 |
|------|---------|------|
| `SparkPackLoader` | Spark-Core | 内容包加载管线全保留 |
| `SparkPackModule` | Spark-Core | 模块接口保留 |
| `SparkMathKt` | Spark-Core | JME↔JOML 转换全保留 |
| `PhysicsHelperKt` | Spark-Core | 物理辅助保留 |
| `GraalVM JS` | Spark-Core | 保留为通用脚本引擎 |
| `MMDynamicRes` | Machine-Max | 零件/子系统资源加载全保留 |
| `SubPart` 物理部分 | Machine-Max | Bullet RigidBody 逻辑不变 |
| `VehicleCore` | Machine-Max | 车辆核心不变 |
| 所有 `subsystem/` | Machine-Max | 子系统逻辑不变 |
| `VehicleBinding`/`SubPartBinding` 业务逻辑 | Machine-Max | 改为 `MechMolangContext` 子类 |
| `PartAnimatable` 变换部分 | Machine-Max | Transform 管理不变 |
| `VehicleAnimatable` | Machine-Max | 变换同步不变 |
| `CollisionHandler` | Machine-Max | 碰撞处理不变 |
| `ProjectileManager` | Machine-Max | 投射物系统不变 |
| SBM 本身 | SBM | 不作任何改动 |

---

## 7. 删除清单（分阶段）

**阶段 B**: Machine-Max
| 文件 | 说明 |
|------|------|
| `MMMolangs.java` | Molang 事件注册（改为 MechMolangContext） |

**阶段 C**: Spark-Core（模型数据）
| 类 | 路径 |
|----|------|
| `OModel` | `animation/model/origin/OModel.kt` |
| `OBone` | `animation/model/origin/OBone.kt` |
| `OCube` | `animation/model/origin/OCube.kt` |
| `OMesh` | `animation/model/origin/OMesh.kt` |
| `OMeshPolygon`/`OMeshVertex`/`OPolygon`/`OVertex`/`OVertexSet`/`OUVUnion` | `animation/model/origin/` |
| `OLocator` | `animation/model/origin/OLocator.kt` |

**阶段 C**: Spark-Core（动画数据）
| 类 | 路径 |
|----|------|
| `OAnimationSet` | `animation/anim/origin/OAnimationSet.kt` |
| `OAnimation` | `animation/anim/origin/OAnimation.kt` |
| `OBoneAnimation` | `animation/anim/origin/OBoneAnimation.kt` |
| `OKeyFrame` | `animation/anim/origin/OKeyFrame.kt` |
| `OAnimStateMachineSet` 等 | `animation/anim/origin/` |

**阶段 C**: Spark-Core（动画运行时）
| 类 | 路径 |
|----|------|
| `AnimController` | `animation/anim/AnimController.kt` |
| `AnimLayer` | `animation/anim/AnimLayer.kt` |
| `AnimInstance` | `animation/anim/AnimInstance.kt` |
| `AnimStateMachine`/`AnimState`/`BlendMode`/`AnimEvent`/`AnimNotify`/`AnimGroups`/`KeyAnimData`/`AnimApplier` | `animation/anim/` |

**阶段 C**: Spark-Core（Molang）
| 类 | 路径 |
|----|------|
| `JSMolangValue` | `js/molang/JSMolangValue.kt` |
| `IMolangContext` | `js/molang/IMolangContext.kt` |
| `MolangRegisterEvent` | `event/MolangRegisterEvent.kt` |
| `JSMolangContext.kt` | `js/molang/` |

**阶段 C**: Spark-Core（模型/渲染）
| 类 | 路径 |
|----|------|
| `ModelInstance` | `animation/model/ModelInstance.kt` |
| `ModelPose` | `animation/model/ModelPose.kt` |
| `BonePose` | `animation/model/BonePose.kt` |
| `IGeoRenderer` | `animation/renderer/IGeoRenderer.kt` |
| `GeoEntityRenderer` | `animation/renderer/GeoEntityRenderer.kt` |
| `GeoBlockEntityRenderer` | `animation/renderer/GeoBlockEntityRenderer.kt` |
| `GeoItemRenderer` | `animation/renderer/GeoItemRenderer.kt` |
| `ModelRenderHelper` | `animation/renderer/ModelRenderHelper.kt` |
| `ModelModule` | `pack/modules/ModelModule.kt` |
| `AnimationModule` | `pack/modules/AnimationModule.kt` |
| `AnimStateModule` | `pack/modules/AnimStateModule.kt` |

**保留的 Spark-Core 类**:
| 类 | 原因 |
|----|------|
| `IAnimatable`/`IEntityAnimatable`/`IBlockEntityAnimatable`/`ItemAnimatable` | Machine-Max 依赖 |
| `ModelIndex` | 索引标识保留 |
| `AnimIndex` | 可选 |
| `SparkPackLoader`/`SparkPackModule` | 内容包管线保留 |
| `SparkMath.kt` | 数学工具保留 |

---

## 8. 风险与缓解

| 风险 | 等级 | 缓解 |
|------|------|------|
| **SBM 路径不兼容** | ⚠️ 高 | 已解决：完全绕过 SBM ResourceManager，直接调用 `bake()`/`createAnimation()` |
| **BoneState 无双缓冲** | ⚠️ 中 | 在 `SBMModelController` 中实现 lerp 缓存 |
| **Molang 语法差异** | ⚠️ 中 | GraalVM JS 执行 JS 语法，MochaEngine 执行 ISL 语法；需检查动画 JSON 中的 Molang 表达式是否符合 ISL 标准 |
| **MochaEngine 不支持带参 @QueryBinding** | ⚠️ 中 | 若 `has_connector(name)` 无法编译，改为暴露 `connector0_exists`/`connector1_exists` 等固定查询 |
| **NeoForge EntityRenderState** | ⚠️ 中 | 需完整实现 `extractRenderState()` + 自定义 RenderState 子类 |
| **SBM MAE 版本锁定** | ⚠️ 低 | SBM JarJar 了 `mae:1.1.3`，跟随 SBM 升级 |
| **性能回归** | ⚠️ 低 | SBM v2 烘焙模型 + chunk 批量渲染性能通常优于当前逐骨骼渲染 |

---

## 9. 关键代码示例

### 9.1 全局 MolangEngine 初始化

```kotlin
object MMMolangEngine {
    /** 全局引擎（仅编译用，不持有 per-instance 状态） */
    val ENGINE: MochaEngine<*> = MochaEngine.create(null) { builder ->
        builder.set("math", JavaObjectBinding.of(MochaMath::class.java, null, MochaMath()))
    }
    
    fun compile(expression: String): MolangExpression {
        return ENGINE.compile(expression, MolangExpression::class.java)
    }
}
```

### 9.2 MolangContext 子类（自定义查询）

```kotlin
/**
 * Machine-Max Molang 上下文。替代 SubPartBinding + VehicleBinding。
 * 
 * 使用方式：
 * - 编译阶段：MolangEngineHelper.createEngine(MechMolangContext::class.java) → MochaEngine
 * - 求值阶段：new MechMolangContext(subPart) → ctx.prepareEvaluation(time) → expr.evaluate(ctx)
 */
class MechMolangContext(entity: SubPart?) : MolangContext<SubPart>(entity) {
    
    constructor() : this(null)  // 无参构造供反射
    
    @QueryBinding("durability", namespace = "subpart")
    fun subpartDurability(): Double = entity?.durability ?: 0.0
    
    @QueryBinding("max_durability", namespace = "subpart")  
    fun subpartMaxDurability(): Double = entity?.maxDurability ?: 0.0
    
    @QueryBinding("is_destroyed", namespace = "subpart")
    fun subpartDestroyed(): Double = if (entity?.isDestroyed == true) 1.0 else 0.0
    
    @QueryBinding("durability", namespace = "veh")
    fun vehDurability(): Double = entity?.vehicleCore?.durability ?: 0.0
    
    @QueryBinding("energy", namespace = "veh")
    fun vehEnergy(): Double = entity?.vehicleCore?.energyGrid?.totalEnergy ?: 0.0
    
    @QueryBinding("max_energy", namespace = "veh")
    fun vehMaxEnergy(): Double = entity?.vehicleCore?.energyGrid?.maxEnergy ?: 0.0
}
```

### 9.3 内容包 → BakedBedrockModel 直接构建

```kotlin
object SparkToSBMBridge {
    
    fun loadModel(reader: Reader): BakedBedrockModel {
        val pojo = GsonUtil.CLIENT_GSON.fromJson(reader, BedrockModelPOJO::class.java)
        val boneNames = collectAllBoneNames(pojo)
        return BakedBedrockModel.bake(pojo, BakerOptions.ofAnimatedBones(boneNames))
    }
    
    fun loadAnimations(reader: Reader, model: BakedBedrockModel): List<BedrockAnimation> {
        val file = GsonUtil.CLIENT_GSON.fromJson(reader, BedrockAnimationFile::class.java)
        return BedrockAnimation.createAnimation(file, model, MMMolangEngine.ENGINE)
    }
    
    private fun collectAllBoneNames(pojo: BedrockModelPOJO): Set<String> {
        return pojo.geometry.flatMap { geo -> geo.bones.map { it.name } }.toSet()
    }
}
```

### 9.4 IAnimatable 实现（SubPart 示例）

```java
public class SubPart extends DestroyableRigidObject implements IAnimatable<SubPart> {
    
    // 内部持有 SBM 控制器
    public final SBMModelController sbmModelController;
    // 旧接口的兼容包装（对 Machine-Max 透明）
    private final ModelController modelControllerCompat;
    private final SBMSimpleAnimator animator;
    
    public SubPart(Part part, SubPartAttr attr) {
        // ...
        this.sbmModelController = new SBMModelController(this);
        this.modelControllerCompat = new ModelController(sbmModelController);
        this.animator = new SBMSimpleAnimator(sbmModelController);
        
        var modelLoc = attr.getVariant().getModel();
        sbmModelController.setModel(new ModelIndex("part", modelLoc));
        sbmModelController.setTextureLocation(attr.getTexture());
    }
    
    @Override
    public ModelController getModelController() { return modelControllerCompat; }
    
    @Deprecated
    @Override
    public AnimController getAnimController() { return null; }
    
    public SBMSimpleAnimator getSimpleAnimator() { return animator; }
    
    public void tickAnimation(float deltaTime) {
        animator.tick();
        var pose = animator.evaluateBlended();
        if (pose != null && sbmModelController.instance != null) {
            sbmModelController.instance.applyPose(pose);
            sbmModelController.snapshotBoneStates();
        }
    }
    
    // 物理部分完全不变...
}
```

### 9.5 SBMSimpleAnimator（AnimationRunner 封装）

```kotlin
class SBMSimpleAnimator(private val controller: SBMModelController) {
    
    private var runner: AnimationRunner? = null
    private val blender = EulerAdditiveBlender()
    
    fun playLooping(animation: BedrockAnimation, clock: AnimationClock) {
        runner = AnimationRunner(animation, AnimationContext(animation.specifiedEndTimeS))
        runner?.setState(LoopingState(clock))
    }
    
    fun playOnce(animation: BedrockAnimation, clock: AnimationClock) {
        runner = AnimationRunner(animation, AnimationContext(animation.specifiedEndTimeS))
        runner?.setState(PlayingState(clock, StopState::new))
    }
    
    fun tick() { runner?.tick() }
    
    fun evaluateBlended(): Pose? {
        val animPose = runner?.evaluate() ?: return null
        val bindPose = controller.instance?.bindPose ?: return null
        return blender.blend(bindPose, animPose)
    }
    
    val isPlaying: Boolean get() = runner != null
}
```

### 9.6 SBMModelController（含 BoneState 插值缓存）

见 Phase 3.1 完整实现（上文已列出，省略重复）。

### 9.7 PartEntityRenderer（NeoForge 1.21.1 EntityRenderState）

```java
@OnlyIn(Dist.CLIENT)
public class PartEntityRenderer extends EntityRenderer<MMPartEntity, PartEntityRenderer.PartRenderState> {
    
    public PartEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }
    
    @Override
    public PartRenderState createRenderState() {
        return new PartRenderState();
    }
    
    @Override
    public void extractRenderState(MMPartEntity entity, PartRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.subPart = entity.subPart;
        state.textureLocation = entity.subPart.getModelController().getTextureLocation();
    }
    
    @Override
    public void render(PartRenderState state, PoseStack poseStack, 
                       MultiBufferSource bufferSource, int packedLight) {
        if (state.subPart == null) return;
        
        var instance = state.subPart.sbmModelController.instance;
        if (instance == null) return;
        if (state.textureLocation == null) return;
        
        var worldMatrix = state.subPart.getRenderWorldPositionMatrix(state.partialTick);
        var entityPos = Vec3.atCenterOf(state.subPart.entity.blockPosition());
        
        poseStack.pushPose();
        poseStack.translate(-entityPos.x, -entityPos.y, -entityPos.z);
        poseStack.mulPose(worldMatrix);
        
        var quadRT = RenderType.entityCutout(state.textureLocation);
        var triRT = RenderType.entityTranslucent(state.textureLocation);
        
        instance.renderToBuffer(poseStack, bufferSource, quadRT, triRT, 
                               packedLight, OverlayTexture.NO_OVERLAY);
        
        poseStack.popPose();
    }
    
    /** 自定义 RenderState 携带 SubPart 引用 */
    public static class PartRenderState extends EntityRenderState {
        SubPart subPart;
        ResourceLocation textureLocation;
    }
}
```

---

## 附录

### A. SBM v2 实际 API 速查

| API | 完整路径 |
|-----|---------|
| `BakedBedrockModel.bake(pojo, options)` | `com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.BakedBedrockModel` |
| `BakedBedrockModel.createInstance()` | → `BedrockModelInstance` |
| `BedrockModelInstance.renderToBuffer(...)` | 委托给 baseModel |
| `BedrockModelInstance.getLocatorTransform(name)` | → `Matrix4f` |
| `BedrockModelInstance.getGlobalTransform(index)` | → `Matrix4f` |
| `BedrockModelInstance.applyPose(pose)` | 从 MAE Pose 应用 |
| `BoneState.x/y/z/rotation/scale/visible` | public 可写字段 |
| `BoneDefinition` | record (pivotX/Y/Z, bindLocalTransform 等) |
| `BoneLocator` | record (name, boneIndex, LocatorData) |
| `QueryTransform` | record (name, attachBoneIndex, localTransform) |
| `BakerOptions.defaults()` | 空集合 |
| `BakerOptions.ofAnimatedBones(set)` | 指定动态骨骼 |
| `BakerOptions.ofAnimationFile(file)` | 从动画收集 |
| `BedrockAnimation.createAnimation(file, model)` | `List<BedrockAnimation>` |
| `BedrockAnimation.createAnimation(file, model, engine)` | 含 Molang 引擎 |
| `BedrockAnimation.specifiedEndTimeS` | 动画时长（秒） |
| `AnimationRunner(anim, AnimationContext(length))` | MAE 动画运行器 |
| `AnimationRunner.setState(LoopingState/PlayingState)` | 播放模式 |
| `AnimationRunner.tick()` / `evaluate()` | 驱动/求值 |
| `MochaEngine.create(entity, scopeBuilder)` | 创建引擎 |
| `MochaEngine.compile(source, MolangExpression.class)` | 编译表达式 |
| `MolangEngineHelper.createEngine(MolangContext子类.class)` | 从类型创建引擎（扫描 @QueryBinding） |
| `MolangContext<T>(entity)` | 创建上下文 |
| `MolangContext.prepareEvaluation(timeS)` | 准备求值 |
| `MolangExpression.evaluate(context)` | 求值 → `double` |
| `@QueryBinding("name", namespace="query")` | 声明自定义查询 |
| `EulerAdditiveBlender.blend(bindPose, animPose)` | MAE 动画混合 |
| `GsonUtil.CLIENT_GSON.fromJson(reader, BedrockModelPOJO.class)` | 解析模型 JSON |
| `AnimationClock` / `AnimationClocks.client()` | 动画时钟 |

### B. 验证检查清单

- [ ] spark_modules 模型正确构建为 `BakedBedrockModel`（骨骼名、pivot 一致）
- [ ] 动画通过 `AnimationRunner` 正常播放
- [ ] 定位器 world transform 与迁移前一致
- [ ] Molang 自定义查询（subpart.durability, veh.energy 等）求值正确
- [ ] PartEntityRenderer 渲染结果与迁移前一致
- [ ] 淡入效果、受击闪烁工作正常
- [ ] BlockEntity 渲染正常
- [ ] 物品模型预览正常
- [ ] 物理碰撞体不受影响
- [ ] 双端启动无崩溃
- [ ] 内容包重载（/reload）正常
- [ ] GraalVM JS 仍可作为脚本引擎使用
