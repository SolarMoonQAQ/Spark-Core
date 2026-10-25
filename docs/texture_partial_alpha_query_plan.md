# Spark-Core 贴图 UV 半透明查询实施计划

> 状态：待实现。本文是交接给编码 agent 的实现契约，不表示相关 API 已存在。
>
> 日期：2026-10-23。目标环境：Minecraft 1.21.1、NeoForge、Java 21、Kotlin。

## 1. 目标与职责

提供**仅客户端使用**的能力：给定实际贴图资源与一个原始 `OCube` / `OBone`，判断其有效 UV 覆盖的源像素是否含有 `0 < alpha < 255` 的像素。返回结果只描述贴图数据，Spark-Core 不据此选择 `RenderType`。

调用方（例如 Machine-Max）负责材质策略、作者覆盖选项、淡入淡出、内构查看、顶点透明度、自发光、混合状态和具体渲染通道。查询服务不改模型、动画、物理、网络载荷或渲染缓冲。

背景案例：Machine-Max 的 2S38 车体使用 alpha 仅含 0 / 255 的贴图，通过 `entityTranslucent` 绘制。在 Iris 1.8.8、BSL 8.2.09 与 AR 1.0.11 环境下出现背景轮廓泄漏；用户实测关闭 BSL Light Shafts 或关闭 AR 实体加速均使现象消失。这个案例用于解释查询能力的用途；本计划的交付不等同于修复 AR / Iris 的绘制时序。

## 2. 当前源码与实现入口

下表均为相对 Spark-Core 根目录的活引用；相邻仓库使用 `../` 路径。实现时先核对实际源码，本文不要求改动每个列出的文件。

| 坐标 | 与本功能有关的事实 |
|---|---|
| `src/main/kotlin/cn/solarmoon/spark_core/animation/model/origin/OCube.kt#OCube` | 原始立方体；`polygonSet` 含有效面与最终顶点 UV；类型是 Kotlin data class |
| `src/main/kotlin/cn/solarmoon/spark_core/animation/model/origin/OBone.kt#OBone` | 原始骨骼；自身几何来自 `cubes` 与 `mesh`；持有到根模型的引用 |
| `src/main/kotlin/cn/solarmoon/spark_core/animation/model/origin/OPolygon.kt#OPolygon` | box UV / per-face UV 的映射在构造时完成；应读取最终顶点 UV，避免再次解释原始 UV 参数 |
| `src/main/kotlin/cn/solarmoon/spark_core/animation/model/origin/OMeshVertex.kt#fromRawData` | mesh 的像素 UV 在构造时归一化；查询时不能再次除以模型声明尺寸 |
| `src/main/kotlin/cn/solarmoon/spark_core/animation/model/origin/OMeshPolygon.kt#OMeshPolygon` | mesh 多边形保存顶点列表 |
| `src/main/kotlin/cn/solarmoon/spark_core/animation/model/origin/OModel.kt#ORIGINS` | 原始模型表，可经内容包读取被清空和填充 |
| `src/main/kotlin/cn/solarmoon/spark_core/animation/model/ModelInstance.kt#textureLocation` | 实际贴图是实例可变属性；模型几何与贴图没有固定配对 |
| `src/main/kotlin/cn/solarmoon/spark_core/animation/model/ModelPose.kt#bonePoses` | 每实例的姿态对象，不是查询缓存的几何键 |
| `src/main/kotlin/cn/solarmoon/spark_core/pack/modules/TextureModule.kt#read` | 把 PNG 字节放进虚拟资源包，不能据此认定该字节就是资源包覆盖后的最终图片 |
| `src/main/kotlin/cn/solarmoon/spark_core/pack/SparkVirtualResourcePack.kt#getResource` | 从当前虚拟包条目取得输入流供应器 |
| `src/main/kotlin/cn/solarmoon/spark_core/pack/SparkPackLoaderApplier.kt#client` | 本地包有 prepare / apply 两个阶段，内容注入与原版纹理重载的顺序需要核对 |
| `src/main/kotlin/cn/solarmoon/spark_core/pack/modules/ModelModule.kt#onStart` | `fromServer` 路径清理原始模型表 |
| `src/main/kotlin/cn/solarmoon/spark_core/pack/SparkPackLoader.kt#readPackageContent` | 读取模块时发布 `PackModuleStartEvent` |
| `src/main/kotlin/cn/solarmoon/spark_core/pack/SparkPackLoader.kt#injectPackageContent` | 发布模块结束、全部结束事件 |
| `src/main/kotlin/cn/solarmoon/spark_core/pack/diagnostic/event/PackModuleStartEvent.kt#PackModuleStartEvent` | 事件可能在资源准备线程或主线程触发，不能假定渲染线程 |
| `src/main/kotlin/cn/solarmoon/spark_core/pack/diagnostic/PackIssue.kt#PackLoadContext` | 客户端本地、客户端远端、服务端加载有独立上下文 |
| `src/main/kotlin/cn/solarmoon/spark_core/pack/sync/SparkPackageReloadPayload.kt#handleInClient` | 远端包重载经主线程队列执行，不能只依赖 F3+T 清理模型结果 |
| `src/main/kotlin/cn/solarmoon/spark_core/registry/client/SparkClientEventRegister.kt#register` | 已有客户端游戏事件注册入口 |
| `src/main/resources/spark_core.mixins.json` 的 `client` 数组 | 如采用纹理解码 Mixin，必须注册在这里，辅助类放在 Mixin 独占包外 |
| `src/main/kotlin/cn/solarmoon/spark_core/compat/accelerated_rendering/ARBoneRenderer.kt#boneMeshes` | 已有按骨骼身份组织的 GPU mesh 缓存；本功能不修改其绘制与缓存策略 |
| `../Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/client/render/renderer/PartEntityRenderer.java#renderTextured` | 下游选择渲染通道的入口，迁移工作独立于本计划 |

`src/main/kotlin/cn/solarmoon/spark_core/event/ModelChangeEvent.kt#ModelChangeEvent` 描述某个动画实例换模型，不是全局原始模型表重载通知。不要给每个实体换模型事件都清空全局缓存。

## 3. API 与结果语义

拟在 `cn.solarmoon.spark_core.client.texture` 提供客户端门面，名称可以随仓库命名习惯调整。以下是拟建 API，不是当前源码引用：

```kotlin
enum class PartialAlphaResult { PRESENT, ABSENT, UNKNOWN }

object TexturePartialAlphaQueries {
    @JvmStatic fun query(texture: ResourceLocation, cube: OCube): PartialAlphaResult
    @JvmStatic fun query(texture: ResourceLocation, bone: OBone): PartialAlphaResult
    @JvmStatic fun request(texture: ResourceLocation) // 可选预热，非阻塞
    @JvmStatic fun invalidate(texture: ResourceLocation)
    @JvmStatic fun invalidateGeometry()
}
```

- `PRESENT`：至少一个有效 UV 区域内存在中间 alpha 源像素。
- `ABSENT`：已完成分析，受支持的有效 UV 区域内没有中间 alpha 源像素。全透明像素 alpha=0 也属于此结果；它与“全不透明”不是同一概念。
- `UNKNOWN`：未就绪、加载失败、资源类别不受支持、UV 无法可靠解释等。调用方自行决定渲染回退；查询服务不能把未知转成 `false`。
- 空 Cube / 空 Bone 的有效几何并集为空，可直接返回 `ABSENT`。
- Bone 只检查**自身** `cubes + mesh`，不递归子骨骼；任一分量为 `PRESENT` 则总体 `PRESENT`，没有 PRESENT 但含 UNKNOWN 则总体 UNKNOWN，其余为 ABSENT。
- 原始对象身份是几何键。不要使用 `BonePose`、动画实体、UUID、骨骼名称、`ModelInstance` 或每次临时创建的包装对象作为几何键。
- 源像素指 PNG 基础图像的 8 位 alpha。结果不模拟 mipmap、双线性过滤、着色器修改、顶点颜色或本帧可见性。读取 alpha 时通过明确的像素格式访问器，不能假定 `NativeImage` 的整型布局与 Java ARGB 相同。
- 查询必须允许 Java 调用；公共签名只出现资源定位、原始几何和结果类型，不暴露 `NativeImage`、`Minecraft`、`RenderType`。
- 服务整体是客户端 API。共通代码不因签名里没有 `net.minecraft.client.*` 就获得调用该服务的许可；分端边界见 §8。

第一版明确支持资源管理器提供的静态 PNG。运行时生成的 GPU 纹理、视频/程序纹理、自定义着色器 UV 变换、动画纹理的帧语义，只有在另行定义并验证后才支持；默认 UNKNOWN。动态纹理不能拿同名磁盘 PNG 冒充当前 GPU 内容。

## 4. 加载期数据与按需组合

### 4.1 纹理分析

每个实际资源版本只准备一次不可变 alpha 索引：实际宽高、是否存在中间 alpha、半透明像素位图，以及必要的采样元数据。禁止每帧扫描像素、读取文件、解码 PNG 或 GPU 回读。

推荐索引是按行排列的位图，每像素一位，置位表示 `0 < alpha < 255`；可附带每行是否有置位的摘要。全图无中间 alpha 时只保存尺寸和标记，不保留像素位图。1024² 的位图为 128 KiB，4096² 为 2 MiB，均不含对象和行摘要开销。禁止默认对每张图保留 32 位二维前缀和；4096² 的该数组约 64 MiB。

资源来源必须与实际采样的贴图一致，并尊重资源包优先级：

1. 优先调查原版 `SimpleTexture#load` / `SimpleTexture.TextureImage#load` 的已解码 `NativeImage`，在其释放前同步提取小型索引，避免第二次解码。具体源码坐标为 `../Machine-Max/build/moddev/artifacts/neoforge-21.1.219-sources.jar!/net/minecraft/client/renderer/texture/SimpleTexture.java#load`（jar 内的源码条目）；若本机生成的依赖版本不同，查阅对应版本的同一源码条目。
2. 解码复用若需要广泛改动，允许第一版对被请求的静态 PNG 通过当前 `ResourceManager` 在后台独立解码一次。成本与“不复用原版解码”的选择必须写入交付报告；渲染线程不得等待它。
3. 不能只在 `TextureModule.read` 分析外部包原始字节；也不能遍历游戏全部 PNG 才允许首次查询。分析范围由实际请求驱动，可对已知配对预热。
4. 独立解码只能分析已确认资源支持的静态纹理。自定义 `AbstractTexture`、动态纹理等没有可靠来源关联时返回 UNKNOWN。
5. 核对资源重载 prepare / apply 与虚拟包更新顺序。后台读取必须绑定所请求的实际资源快照；重载切换期间不发布可能来自错误版本的结果。若不能取得与纹理上传一致的内容快照，采用解码复用入口，或保持 UNKNOWN。

`NativeImage` 与输入流归属必须明确：复用他人解码结果时不关闭他人对象；自有解码对象使用 `use` / `try-with-resources`，成功、失败、失效任务三条路径都释放。缓存仅保存 JVM 内存中的不可变索引。

### 4.2 几何分析

模型与贴图解耦，所以模型加载时最多整理无贴图依赖的 UV 数据，不能写出一个全局 `isTranslucent` 字段。第一版可以直接在组合查询 miss 时读取最终 UV；不要求另建常驻 UV 描述缓存。

纹理索引 READY 后，首次查询 `(原始对象身份, 纹理 token)` 才计算组合结果；随后命中结果缓存。整张图没有中间 alpha 时，受支持的几何可走快速路径，不必为每个对象创建相同的 ABSENT 条目。

骨骼动作只改变空间变换时，组合结果有效。已发布的原始 UV 按不可变数据使用；工具若原地编辑 UV，必须调用客户端 `invalidateGeometry()`，或发布全新的原始对象。身份键本身不能侦测对象内部被修改。

## 5. UV 覆盖规则

### 5.1 Cube

- 读取 `polygonSet` 的最终 `vertexes`，与绘制路径使用同一条“无 UV 面”排除规则。该规则可定位到 `OCube.kt#renderVertexes` 与 `ARBoneRenderer.kt#render`；不要把任意零面积 UV 都等同于无面。
- 不依赖当前相机、不做背面或小面剔除。查询描述该几何可能使用的贴图区域。
- UV 已归一化；乘以**实际 PNG 尺寸**映射到源像素，不能乘回模型声明尺寸后就直接当作实际图片像素。模型声明 256²、资源包提供 1024² 覆盖图时应得到正确结果。
- 正常轴对齐 UV 矩形使用半开像素范围；针对 min/max 的取整、UV=0/1 与整数边界写出固定约定及测试。镜像、负 UV 尺寸经最终顶点 min/max 处理。
- 点 / 线退化 UV 可能在采样同一像素或一列像素，须专门处理；不能自动判为空。
- UV 超出 [0,1] 时不能盲目截断。若已拿到与 GPU 一致的 clamp / repeat 信息，按该模式拆分区域并测试；没有可靠元数据或实现时返回 UNKNOWN。
- 非有限坐标、无法验证的纹理尺寸或越界整数计算返回 UNKNOWN，不能凭默认值判为 ABSENT。

### 5.2 Bone 与 mesh

Bone 结果是自身各 Cube 与 mesh 覆盖区域的并集。mesh 最终 UV 可从 `OMesh.kt#polygons`、`OMeshPolygon.kt#vertices`、`OMeshVertex.kt#uv` 取得。

对于三角形 / 四边形，按实际绘制采用的三角划分判断 UV 多边形与源像素单元的交集；第一步可以用包围框快速排除。**包围框内存在中间 alpha，不等于三角形内部存在**，不能把包围框的阳性直接报告为 PRESENT。坐标边界与退化三角形的采样约定应与 Cube 的规则一致。

编码 agent 必须核对普通与 AR 路径实际支持的多边形及三角划分。无法匹配的多边形类型、UV 自交、超大重复展开等返回 UNKNOWN；不得为完成检测而改动 mesh 绘制算法。允许分阶段先交付 Cube 查询，但最终提交必须明确列出 Bone 的 mesh 支持范围；测试不能把漏查 mesh 的 ABSENT 当成功。

## 6. 缓存结构、上界与引用关系

建议使用三个全局有界缓存，而不是给每个 TextureEntry 都配置一份大容量几何缓存，避免上界随着贴图数量相乘。

```text
TextureIndexCache<ResourceLocation, TextureEntry>
  TextureEntry = textureToken + dimensions + samplingMetadata + immutableAlphaIndex

CubeResults<OCube, SmallTextureResults>     // 弱身份键，有全局数量上限
BoneResults<OBone, SmallTextureResults>     // 弱身份键，有全局数量上限
  SmallTextureResults = 有界的 [textureToken -> PRESENT / ABSENT]
```

### 6.1 弱身份键

- 可以使用 Guava `CacheBuilder.weakKeys()`，它按对象身份比较；同时设置 `maximumSize`。普通 `WeakHashMap` 使用对象 equals/hashCode，不符合这里的身份契约。
- `OCube` / `OBone` 是 data class；结构相同的两个对象可以相等，可变属性还会影响普通哈希。测试必须证明两个不同身份不会错误共用条目。
- `SmallTextureResults` 只保存原始类型 token 与枚举，不保存 Cube、Bone、ModelInstance、OModel、UV 对象的父链或捕获几何键的闭包。值到键的任何强引用路径都会使弱键失效。
- 不要对 `Pair(cube, texture)` 使用 `weakKeys()`：弱化的是临时 Pair，不能表达原始 Cube 的生命周期。几何对象本身必须是弱键。
- 缓存惰性 miss 允许复算；不要求用 `System.gc()` 维持运行正确性。定期维护应使用缓存自带的 `cleanUp()`。

### 6.2 容量与 token

下列数值是**第一版可调的初始预算，不是实测最优值**；实现可用命名常量集中表达，暂不要求对玩家暴露配置界面：

| 项目 | 初始预算 / 规则 |
|---|---|
| 纹理索引 | 64 MiB 加权预算；计入位图、行摘要和固定条目成本；最小计费 16 KiB，限制大量无位图条目 |
| Cube 弱键缓存 | 全局最多 32768 个对象条目 |
| Bone 弱键缓存 | 全局最多 16384 个对象条目 |
| 每个几何条目的贴图结果 | 最多 4 个 token，命中更新小型 LRU；固定数组即可，无需每帧创建 Pair |
| 独立后台分析 | 同一资源版本请求合并；工作任务与等待队列均有上限，例如 1 个 worker、最多 64 个待分析资源 |
| 失败重试 | 按资源版本限频，例如失败后至少 5 秒再试；日志按资源与原因去重 |

所有纹理条目，包括 ABSENT 快速标记、失败标记、排队请求，都应有数量或内存上限；不能只给位图设置上限。单张图片的解码尺寸 / 峰值内存也要有明确限制，超限返回 UNKNOWN，并记录限制原因。

`textureToken` 为客户端进程内不复用的递增标识。资源内容重载、显式失效后重建、索引被淘汰后重新构建，都分配新 token。几何值只保存 token，不持有 TextureEntry，因此不能阻止大位图淘汰。token 不同步、不序列化、不暴露为模型属性。

UNKNOWN 不作为永久的几何判定写入缓存。未就绪与失败资源通过独立、有界的请求状态与限频记录避免每帧解码或刷日志。

## 7. 失效时机与线程

### 7.1 生命周期矩阵

| 事件 | 必须完成的动作 |
|---|---|
| 普通换涂装 / 换模型实例 | 使用新贴图资源或新原始对象查询；不为单个实例清空全局缓存 |
| 贴图显式更新 | `invalidate(texture)`，使该资源正在进行的任务及当前 token 失效；其他资源可保留 |
| 客户端资源重载 | 建立新资源 epoch；停止发布先前资源结果，清索引、请求状态、几何结果；资源提交完成后允许请求新版本 |
| 原始模型表重载 | 建立新几何 epoch，清 Cube / Bone 结果；可以保留有效纹理索引 |
| 原地修改 UV | 调用 `invalidateGeometry()`；修改与查询在约定的客户端线程顺序执行 |
| 退出服务器 / 断开连接 | 推进会话 epoch；清几何、请求及纹理缓存，释放完成队列结果；下次会话按需建立 |
| 旧任务晚到 | 对照会话、资源版本、请求 token，拒绝发布；释放自有资源，不能覆盖当前条目 |

### 7.2 现有事件接线

优先复用 §2 的内容包事件，监听 `PackModuleStartEvent` / `PackModuleFinishEvent` 中 `moduleId == "models"` 且上下文为 `CLIENT_REMOTE` 的路径，处理初次远端模型下发与远端重载。模型读取失败时也须结束本次失效流程，不能让查询永久停在“加载中”。新对象身份会自然 miss，但不能以此代替旧结果清理。

本地纹理更新通过客户端资源重载生命周期处理。不要把 `PackAllFinishedEvent(CLIENT_LOCAL)` 直接当作所有原版纹理已经上传完毕的证明；核对虚拟资源包注入与纹理资源版本后再开放请求。

物理客户端中的集成服务器也会发布服务端上下文事件。监听器只处理约定的客户端上下文，不在服务端物理线程访问 Minecraft 或修改客户端容器。现有原始模型表的双端共享问题不在本计划中扩展重构。

### 7.3 线程所有权

- 门面查询、几何结果缓存和就绪索引的发布由客户端主 / 渲染线程持有。方法契约须说明线程要求；禁止在该线程等待 Future 或做文件 I/O。
- 纹理解码 / 索引构建可以在后台进行，产出不可变数据；worker 不读取 BonePose、不持有原始几何对象、不调用渲染 API。
- 任意线程触发失效时，同步推进原子 epoch，然后把容器清理排进客户端队列。查询先核对 epoch，不能在清理任务执行前继续使用旧 epoch 的结果。
- 每次查询与异步发布都需验证入口取得的 epoch，避免计算期间发生失效后仍返回或缓存旧结果。
- 工作任务绑定资源快照和会话 / 资源 token；完成后在客户端线程校验并发布。只执行 `future.cancel()` 不能保证旧任务不会发布。
- 资源重载准备线程可能早于可用的 Minecraft 主循环。事件入口可以更新纯原子状态，客户端对象访问须通过已初始化的客户端入口完成。

## 8. 物理客户端隔离

查询门面、NativeImage 适配器、缓存实例和生命周期监听器全部位于客户端实现中。依赖方向是 `客户端查询实现 -> 共通原始模型`。

硬约束：

1. `OCube`、`OBone`、`OModel`、ModelInstance 及它们的 codec 不引用查询服务；不添加客户端缓存字段、客户端返回类型或触发服务初始化的默认属性。
2. 共通加载器与网络 handler 不直接调用该客户端服务。使用已有的共通事件；若确有缺失，只增加参数不含客户端类型的通知。
3. 模组总线与游戏总线监听器由显式 `Dist.CLIENT` 限定的入口注册。可用独立的客户端 `@EventBusSubscriber`；不要在共通监听类的方法签名里增加客户端事件类型。
4. `@OnlyIn`、client 包名和方法体中的 `isClientSide` 判断都不能代替注册与类依赖隔离；不以物理客户端中的“逻辑服务端”判断代替物理发行版隔离。
5. 如采用纹理解码 Mixin，只把 Mixin 类放入 `spark_core.mixins.json` 的独占包并登记到 `client` 数组；可被普通代码调用的缓存和算法类放在独占包外。
6. 不借本功能重构 §2 中全部既有分端代码。交付须用真实专用服务器启动证明本功能没有引入客户端类加载路径。

## 9. 分阶段任务与文件建议

全部路径均是**拟创建文件**，编码 agent 可合并小类，但须保留可独立测试的算法与明确的客户端入口。

| 阶段 | 交付 |
|---|---|
| P0：契约与分端 | 客户端 `PartialAlphaResult`、查询门面、客户端注册空壳；确认共通模型无反向依赖 |
| P1：纹理索引 | 纯像素索引算法、静态 PNG 适配、资源快照 / epoch、请求去重与预算；取舍解码复用或一次后台解码 |
| P2：几何查询 | Cube 有效面规则、像素覆盖边界、Bone 聚合及 mesh 支持矩阵；查询不依赖动画姿态 |
| P3：缓存与清理 | 弱身份键、每几何有限贴图槽、全局上限、纹理淘汰 token、重载与断线、晚到任务拒绝 |
| P4：验证与交接 | 单测、真实专服、客户端资源重载、性能读数、Java 调用示例与支持范围说明 |

建议文件位于 `src/main/kotlin/cn/solarmoon/spark_core/client/texture/`：`TexturePartialAlphaQueries.kt`、`PartialAlphaResult.kt`、`TextureAlphaIndex.kt`、`GeometryAlphaProbe.kt`、`TexturePartialAlphaCache.kt`、`TexturePartialAlphaLifecycle.kt`。纯算法不访问 Minecraft 单例；JNI 图片适配与客户端注册独立。

如需 Mixin，拟使用 `src/main/java/cn/solarmoon/spark_core/mixin/resource/SimpleTextureAlphaMixin.java`。先确认实际注入点与图片归属，再决定是否创建此文件。

第一版不改 Machine-Max 的渲染通道、不修改 AR GPU mesh 缓存、不增加内容包材质配置或协议字段。给下游提供调用示例即可；调用方根据 UNKNOWN 自行选择策略。

## 10. 验收与性能测量

### 10.1 自动化测试

测试放在 `src/test/` 的普通包内，不放在 Mixin 独占包。使用合成小图片和可控资源供应器，避免为纯算法启动 OpenGL。

- alpha=0、255、1、254；无 alpha 通道图片；区分 ABSENT 与 UNKNOWN。
- 同图中独立的玻璃区域与实体区域；同一 Cube / Bone 对不同贴图得到不同结果。
- 同一个实际对象重复命中；两个结构相等但身份不同的 data class 不误命中。
- box UV、per-face UV、镜像、非整数边界、UV=0/1、实际图片尺寸与模型声明尺寸不同、有效点 / 线 UV、无 UV 面。
- 非有限 UV、超范围 UV 的支持模式、未支持模式返回 UNKNOWN。
- Bone 不递归子骨骼；自身 cubes 与 mesh 的三态聚合；mesh 包围框内、实际三角形外的中间 alpha 不误报 PRESENT。
- 首次 UNKNOWN 到 READY 的转换；相同资源请求去重；失败限频；UNKNOWN 不固化为 ABSENT。
- 满容量淘汰后结果可重新计算；所有强持有容器、失败记录、待分析队列均不超过预算；检查实际条目而非只检查配置。
- 资源包覆盖、同资源路径换像素、模型重载、原地 UV 失效、断线换服；相同几何与路径不使用失效 token。
- 阻塞旧任务、推进 epoch、发布新结果，再放行旧任务；旧结果不能覆盖新结果，也不能从失效中的 query 返回。
- 用测试缝隙 / 可控引用队列检查弱键清理与值到键无引用链；不要写依赖一次 `System.gc()` 就必然回收的脆弱断言。
- Java 源码调用门面可以编译；编译产物检查共通模型没有到客户端查询服务的类型依赖。

### 10.2 实际运行

命令使用仓库 wrapper、Java 21，全部带 `--offline`。Spark-Core 的运行目录配置可定位到 `build.gradle.kts` 的 `runs` 块。

```powershell
# Spark-Core 根目录
.\gradlew --offline test
.\gradlew --offline build
.\gradlew --offline runServer
.\gradlew --offline runClient
```

专服达到正常就绪后用控制台 `stop` 正常退出，核对没有本功能导致的客户端类解析错误；已有依赖 / 环境错误须与本功能分开记录。单元测试成功不能代替专服验证。

客户端验证：同一模型切两张 alpha 分布不同的贴图；多实体共享模型和纹理；F3+T 修改同路径 PNG；内容包远端模型重载；退出并进入另一会话；资源包覆盖分辨率。分别记录 query 结果、缓存命中和 epoch，不以肉眼看到“不透明”代替查询契约验证。

允许在 MM 开发客户端临时消费 API 对照 cutout / translucent，但临时诊断改动须独立记录和清理，不能混入本功能正式实现。BSL / AR 的兼容性归因与查询正确性分开验收。

### 10.3 性能与交付证据

记录 1024²、4096² 以及一张混合透明材质的实际贴图：解码 / 索引构建时间、额外解码次数、索引保留字节、首次 Cube / Bone 查询耗时、稳定命中耗时和分配、批量首次查询对帧时间的影响。没有测量就不承诺毫秒数或 FPS 增益。

稳定命中路径只读取状态 / token 与有界结果表，不读取源像素、不遍历全部面、不创建复合键。缓存 miss 的 UV 检查属于一次性成本；若大模型首次查询产生明显尖峰，可预热或在不强持有模型的前提下准备有界的纯 UV 数据任务，并复测，不能把重计算直接推给每一帧。

交付报告必须包含：已实现的 API 与 UNKNOWN 支持矩阵、缓存预算和实际峰值、资源更新与任务失效验证、专服启动结果、性能读数、剩余限制。本文状态只有在对应验收完成后才能更新。

## 11. 外部依据

- [Guava CacheBuilder](https://guava.dev/releases/33.4.7-jre/api/docs/com/google/common/cache/CacheBuilder.html)：`weakKeys()` 的身份比较、弱引用维护与缓存容量控制；实际使用 API 需对照项目依赖版本。
- [NeoForge 1.21.1 Sides](https://docs.neoforged.net/docs/1.21.1/concepts/sides/)：物理客户端与逻辑端区别、客户端类加载隔离。
- [NeoForge 1.21.1 Resources](https://docs.neoforged.net/docs/1.21.1/resources/)：客户端资源与资源包优先级。
- [Khronos Transparency Sorting](https://wikis.khronos.org/opengl/Transparency_Sorting)：源 alpha、cutout、混合与过滤的区别；本功能只报告源像素事实。
