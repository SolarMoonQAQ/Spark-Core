# Libbulletjme 双精度（Dp）构建切换评估

> 评估日期：2026-10-04
> 评估对象：`Spark-Core` 物理层（`src/main/java/com/jme3/bullet` + `src/main/kotlin/cn/solarmoon/spark_core/physics`）
> 结论：**可行，且实施方式已定**（决策清单见 §6）。Maven Central 有现成的 Dp / DpMt 构件，内嵌 Java 层的 `native` 方法名集合与上游完全一致，Java 层零改动；对外物理 API 采取**只加不改**。
> 代价与风险集中在三处：**切换前的 native 选型**（§2：多线程构件不在加载路径上）、**Kotlin 层的坐标管线**（§4.4）、**加载器要支持按平台的候选列表**（§7）。
>
> **实施状态：已实施并通过验证**（§11 记录了实际落地内容、与本文计划的三处取舍差异、以及验证证据）。
> 因此文中 **§2 描述的是切换前的基线**，`natives/bullet` 与桌面 / macOS 的 `natives/bullet_sp` 已经不存在；
> 现在的投放是 `natives/bullet_dpmt` + `natives/bullet_dp` + `natives/bullet_sp/android`。
> 其余章节（§3–§7）描述的是切换后的代码与投放现状，§8–§10 是落地步骤与复现方式。

---

## 1. 背景与问题确认

物理引擎（Bullet / Libbulletjme）以 `float` 计算，而 Minecraft 实体位置是 `Vec3`（`double`）。
Spark-Core 的物理层**使用绝对世界坐标**，没有浮动原点（floating origin）机制。
下表是**评估时**绝对坐标进入物理层会经过的 float 窄化点，以及它们现在（切换后）的状态：

| 位置 | 代码 | 说明 |
|---|---|---|
| 坐标转换 | `src/main/kotlin/cn/solarmoon/spark_core/physics/PhysicsHelper.kt#toBVector3f` | `Vector3f(x.toFloat(), y.toFloat(), z.toFloat())`。仍保留给 float 通路，新增 `#toVec3d` 与 `#absoluteAabb` |
| 实体刚体 | `src/main/kotlin/cn/solarmoon/spark_core/physics/body/RigidBodyEntity.kt#setPosRaw` | 切换前 `Vector3f(x.toFloat(), y.toFloat(), z.toFloat()).toBVector3f()`；现为 `setPhysicsLocationDp(Vec3d(x, y, z))` |
| 附着实体 | `src/main/kotlin/cn/solarmoon/spark_core/physics/body/PhysicsBodyExtension.kt#attachToEntity` | 切换前 `entity.boundingBox.center.add(...).toBVector3f()`；现走 Dp。同文件的 `#attachToBone` / `#attachToLocator` 刻意保持 float（源矩阵是 `Matrix4f`，见 §4.4） |
| 地形 section | `src/main/kotlin/cn/solarmoon/spark_core/physics/terrain/PhysicsChunkSection.kt#createPhysicsBody` | 切换前 `setPhysicsLocation(Vector3f(minBlockX() + 8f, ...))`；现为 `setPhysicsLocationDp(Vec3d(...))` |
| 世界范围 | `src/main/kotlin/cn/solarmoon/spark_core/physics/level/PhysicsWorld.kt#PhysicsWorld` | 切换前 `±Int.MAX_VALUE.toFloat()` → ±2,147,483,648；现为 `src/main/kotlin/cn/solarmoon/spark_core/physics/level/PhysicsSpaceBounds.kt#PhysicsSpaceBounds`（§6 的 D9） |
| 主线程位姿回读 | `src/main/kotlin/cn/solarmoon/spark_core/physics/body/PhysicsBodyState.kt#transform` | `body.getTransform(null)`（float `Transform`）保留给下游与渲染；新增 `#absoluteLocation` / `#absoluteAabb` |
| 快照世界位姿复制 | `src/main/kotlin/cn/solarmoon/spark_core/physics/level/WorldSnapshot.kt#syncTransform` | 切换前 `snap.setPhysicsTransform(main.getTransform(null))`；现走 `#syncPose`（Dp 位置 + Dp 姿态） |

`src/main/kotlin/cn/solarmoon/spark_core/physics/terrain/PhysicsChunkManager.kt#updateActivation` 有两个重载：接受 `List<AABB>` 的那个由 `src/main/kotlin/cn/solarmoon/spark_core/physics/level/PhysicsLevel.kt#requestStep` 每 tick 调用；接受单个 `focusPos: Vector3f` 的那个没有任何调用点，属于死代码。
所谓「局部化」只限制**刚体数量**，不改变**坐标数值大小**。

### float 在多大距离上开始失效

| 距离（方块） | float ULP（相邻可表示值间距） | 说明 |
|---|---|---|
| 1,000 | 0.0001 | 无感 |
| 100,000 | 0.0078 | 无感 |
| 524,288 (2^19) | 0.0625 | Bullet 默认碰撞 margin 0.04（`src/main/java/com/jme3/bullet/collision/shapes/CollisionShape.java#defaultMargin`）低于 1 ULP |
| 1,000,000 | 0.0625 | 勉强 |
| 10,000,000 | 1.0000 | 明显失效 |
| 16,777,216 (2^24) | 2.0 | float 不再能精确表示所有整数 |
| 29,999,984（原版世界边界） | 2.0 | 人物碰撞体宽 0.6 格 → 位置被量化到 2 格栅格 |
| 2,147,483,648（切换前本 mod 声明的世界范围） | 256.0 | 完全无意义 |

实测（把 `double` 转 `float` 再转回）：

```
真实位置            存为 float         误差(格)   判定
100,000.3000        100,000.2969       0.0031     可接受
1,000,000.3000      1,000,000.3125     0.0125     可见
10,000,000.3000     10,000,000.0000    0.3000     破坏碰撞
29,999,984.7000     29,999,984.0000    0.7000     灾难性
```

在 30,000,000 附近，float 的相邻可表示值间距是 2 格，因此每 0.1 格采样的 101 个请求位置会**塌缩成 6 个不同的 float 值**。

---

## 2. 切换前的投放基线

> 本节是**切换前**的投放与加载行为，用来解释「为什么要换」。
> 切换后的实际投放见 §4.2 的目标布局，加载器的现行为见 §7.3，落地记录见 §11。

### 2.1 目录与 flavor

切换前的加载入口是 `src/main/kotlin/cn/solarmoon/spark_core/SparkCore.kt#SparkCore` 中的
`NativeLoader.load(MOD_ID, "bullet", fallbackModuleName = "bullet_sp")`；
该三参形态已经在实施中被平台候选表取代（见 §7.3、§11）。

仓库投放的 11 个 native 文件（`src/main/resources/natives/`，共 37,222,568 字节）实测/推断如下：

| 投放路径 | 平台 | `isDoublePrecision` | `isThreadSafe` | 实际 flavor |
|---|---|---|---|---|
| `natives/bullet/windows/x86_64`、`natives/bullet/linux/x86_64`、`natives/bullet/linux/arm64` | Windows / Linux | false | **false** | SpRelease |
| `natives/bullet_sp/windows/x86_64`、`natives/bullet_sp/linux/x86_64`、`natives/bullet_sp/linux/arm64` | Windows / Linux | false | **true** | SpMtRelease |
| `natives/bullet_sp/macos/x86_64`、`natives/bullet_sp/macos/arm64` | macOS | false | false | SpRelease |
| `natives/bullet_sp/android/{arm64-v8a, armeabi-v7a, x86_64}` | Android | false | false | SpRelease |

依据分三层，均可复现（命令见 §10）：

- **JNI 实测**（Windows 三个 DLL）：`NativeLibrary.isDoublePrecision()` / `isThreadSafe()` 分别为 false/false、false/true、true/true；启动横幅分别为 `Libbulletjme version 23.1.0 initializing`、`Mt_Libbulletjme ...`、`Dp_Mt_Libbulletjme ...`。横幅前缀由 `../Libbulletjme/src/main/native/glue/jmeClasses.cpp#BT_USE_DOUBLE_PRECISION` 处的宏决定。
- **动态依赖扫描**（全部 11 个文件）：只有 `natives/bullet_sp` 的 Windows / Linux 构件含 OpenMP 运行时依赖（Windows 为 `VCOMP` + `omp_get_max_threads`，Linux 为 `libgomp` + `GOMP_parallel`）；`natives/bullet` 的全部构件与 `natives/bullet_sp` 的 macOS / Android 构件都没有。
- **上游构建规则**：macOS 被排除在 Mt 之外（`../Libbulletjme/build.gradle` 中 Mt flavor 的可构建条件为 `buildable = !pName.contains('Loong') && (os != 'osx') && pName.contains('64')`），Android 的 `../Libbulletjme/android.gradle` 只定义 `Dp` 与 `Sp` 两个精度 flavor，没有 Mt。

**结论：切换前没有任何双精度 native 被使用，仓库里也不存在 Dp 构件。**

### 2.2 切换前加载顺序的实际后果

`src/main/kotlin/cn/solarmoon/spark_core/pack/NativeLoader.kt#load` 先试主库、失败才试回退库，而 `natives/bullet/` 覆盖了 Windows 与 Linux：

- **Windows / Linux**：主库 `natives/bullet/...` 加载成功即返回，因此 **SpMt 构件不在加载路径上**，Bullet 以单线程求解器运行，`NativeLibrary.countThreads()` 返回 1（实测）。
- **macOS / Android**：`natives/bullet/` 下没有对应目录，资源取不到即失败，必然走回退分支加载 `natives/bullet_sp/...`（同为非 Mt），并在每次启动打印一段 `====== 多线程物理库 (bullet) 加载失败 ======` 错误块。

当时 `src/main/kotlin/cn/solarmoon/spark_core/pack/NativeLoader.kt#load` 的 KDoc 与日志把 `moduleName` 描述为「主库（如多线程版本 SpMt）」、把 `fallbackModuleName` 描述为「单线程版」；实测投放的 flavor 与之相反：`bullet` 非 Mt，`bullet_sp` 在 Windows / Linux 是 Mt、在 macOS / Android 是非 Mt。这是描述与投放不一致的问题，并且它有功能后果：Mt 求解器不在加载路径上（性能量化见 §4.1）。

### 2.3 切换后的投放（现状）

| 目录 | 文件数 | 字节数 | flavor |
|---|---|---|---|
| `natives/bullet_dpmt/{windows,linux}` | 3 | 10,428,992 | DpMtRelease |
| `natives/bullet_dp/{windows,linux,macos}` | 5 | 16,639,352 | DpRelease |
| `natives/bullet_sp/android` | 3 | 10,444,024 | SpRelease（与切换前同一份文件） |

- 上述 11 个文件共 37,512,368 字节，相对切换前的 37,222,568 字节 **+289,800 字节（+0.8%）**。
- §2.2 描述的两个问题随之消失：桌面 / macOS 的首个候选就是实际落点，不存在「主库失败 → 打印错误块 → 回退」的路径；Mt 求解器进入加载路径（`countThreads()` 由 1 变为 2）。
- §2.1 的三层依据对现在的三个目录同样成立，只是路径不同：`natives/bullet_dpmt` 含 OpenMP 运行时依赖（Windows `VCOMP`、Linux `libgomp`），`natives/bullet_dp` 与 `natives/bullet_sp/android` 不含。

---

## 3. 切换可行性：非常可行

### 3.1 现成构件（无需自行编译）

Maven Central 上 `com.github.stephengold:Libbulletjme-<platform>:23.1.0` 与当前内嵌 Java 层声明的版本一致
（`src/main/java/com/jme3/bullet/util/NativeLibrary.java#expectedVersion` = `"23.1.0"`）。
各平台的可构建 flavor 由上游构建规则决定（`../Libbulletjme/build.gradle`），发布命令见 `../Libbulletjme/.github/workflows/push.yml`：

| 平台 | Sp | SpMt | Dp | DpMt | 依据 |
|---|---|---|---|---|---|
| Windows x86_64 | ✅ | ✅ | ✅ | ✅ | Maven Central 清单 + 已下载核对（Dp / DpMt） |
| Linux x86_64 | ✅ | ✅ | ✅ | ✅ | 已下载核对（Dp / DpMt） |
| Linux ARM64 | ✅ | ✅ | ✅ | ✅ | 已下载核对（Dp / DpMt） |
| macOS x86_64 | ✅ | ❌ | ✅ | ❌ | 已下载核对（Dp）；Mt 被上游排除 |
| macOS ARM64 | ✅ | ❌ | ✅ | ❌ | 已下载核对（Dp）；Mt 被上游排除 |
| Windows ARM64 | ✅ | ✅ | ✅ | ✅ | 上游构建规则（本仓库不投放该平台） |
| Linux ARM32hf | ✅ | ❌ | ✅ | ❌ | 上游构建规则（Mt 要求 64 位） |
| Android | ✅ | ❌ | ❌（未发布） | ❌ | Maven Central 清单（仅 SpDebug / SpRelease 两个 AAR）；`../Libbulletjme/android.gradle` 定义了 Dp flavor，但发布流程只跑 `-Pflavor=Sp`，详见 §4.3 |

已下载核对的桌面构件（`23.1.0`，jar 内的 native 路径与字节数）：

| 构件 | 路径中的平台段 | native 路径 | 字节数 |
|---|---|---|---|
| `Libbulletjme-Windows64-23.1.0-DpMtRelease.jar` | `windows` | `native/windows/x86_64/bulletjme.dll` | 2,439,168 |
| `Libbulletjme-Windows64-23.1.0-DpRelease.jar` | `windows` | `native/windows/x86_64/bulletjme.dll` | 2,415,616 |
| `Libbulletjme-Linux64-23.1.0-DpMtRelease.jar` | `linux` | `native/linux/x86_64/libbulletjme.so` | 4,380,584 |
| `Libbulletjme-Linux64-23.1.0-DpRelease.jar` | `linux` | `native/linux/x86_64/libbulletjme.so` | 4,370,584 |
| `Libbulletjme-Linux_ARM64-23.1.0-DpMtRelease.jar` | `linux` | `native/linux/arm64/libbulletjme.so` | 3,609,240 |
| `Libbulletjme-Linux_ARM64-23.1.0-DpRelease.jar` | `linux` | `native/linux/arm64/libbulletjme.so` | 3,590,792 |
| `Libbulletjme-MacOSX64-23.1.0-DpRelease.jar` | **`osx`** | `native/osx/x86_64/libbulletjme.dylib` | 3,455,016 |
| `Libbulletjme-MacOSX_ARM64-23.1.0-DpRelease.jar` | **`osx`** | `native/osx/arm64/libbulletjme.dylib` | 2,807,344 |

> ⚠️ **打包陷阱**：构件里 macOS 的平台段是 `osx`，而加载器的平台路径是 `macos`（`src/main/kotlin/cn/solarmoon/spark_core/pack/NativeLoader.kt#getPlatformPath`）。直接解包到模块目录会落成 `natives/<module>/osx/...`，加载器取不到 → macOS **静默回退**。投放时必须映射成 `natives/<module>/macos/<arch>/libbulletjme.dylib`。Windows / Linux 的平台段与加载器一致，无需映射。

实测加载 `23.1.0-DpMtRelease`：

```
versionNumber     = 23.1.0      ← 与 NativeLibrary.expectedVersion 一致
isDoublePrecision = true
isThreadSafe      = true
启动横幅: Dp_Mt_Libbulletjme version 23.1.0 initializing
```

### 3.2 Java 层零改动

- **Java 完全不分支于精度**：`src/main/java/com/jme3/bullet/util/NativeLibrary.java#isDoublePrecision` 在整个 `src/` 中**零调用点**。
- 两棵树的 `native` 方法名集合**完全一致**（无任何一侧独有的名字），JNI 解析不受影响；提取口径与命令见 §10。
- Spark-Core 的内嵌库**没有被裁剪**：上游 `23.1.0` tag 的 `src/main/java` 含 177 个 `.java`，本地库部分（`src/main/java` 中除 `cn/solarmoon/**` 与 `au/edu/federation/**` 之外的 179 个文件）覆盖了全部 177 个，另有 2 个仅本地存在的文件（`src/main/java/fl/Lathe.java`、`src/main/java/fl/FunctionLathe.java`）。
  剔除行尾空行差异后，仍有约 40 个文件与上游文本不同（多为 javadoc 全限定名与通配符 import 的风格差异）；相对上游 `23.1.0`，**代码级差异只有 4 个文件**：
  `src/main/java/com/jme3/bullet/collision/PhysicsCollisionObject.java`（比上游多出事件机制与若干便利字段/方法）、
  `src/main/java/com/jme3/bullet/PhysicsSpace.java`（比上游多出 `update(timeInterval, maxSteps, doEnded, doProcessed, doStarted, doPreAdd)` 重载）、
  `src/main/java/com/jme3/bullet/collision/shapes/CollisionShape.java`（比上游多出 `boundingBoxWithoutRecalculate`，已被 `src/main/kotlin/cn/solarmoon/spark_core/physics/body/PhysicsBodyState.kt#transform` 使用）、
  `src/main/java/com/jme3/bullet/collision/shapes/CompoundCollisionShape.java`（`addChildShape` 返回 `ChildCollisionShape`，上游同方法返回 `void`）。
  四处都不触碰 `native` 名。
- 所需的双精度 API 已经存在于内嵌 Java 层：`src/main/java/com/jme3/bullet/collision/PhysicsCollisionObject.java#getPhysicsLocationDp`、`src/main/java/com/jme3/bullet/objects/PhysicsRigidBody.java#setPhysicsLocationDp`、同文件的 `getLinearVelocityDp` / `setPhysicsRotationDp`、`src/main/java/com/jme3/bullet/collision/PhysicsCollisionObject.java#getTransformDp`。
  且 `src/main/java/com/jme3/bullet/objects/infos/RigidBodySnapshot.java#getPhysicsLocationDp` 与 `src/main/kotlin/cn/solarmoon/spark_core/physics/body/ManifoldPoint.kt#getPositionWorldDp` **已经在用** Dp 入口。
  缺口只有一处：`PhysicsRigidBody` 有 `src/main/java/com/jme3/bullet/objects/PhysicsRigidBody.java#setPhysicsTransform`（float 版），**没有对应的 `setPhysicsTransformDp`**，写侧需要拆成 `setPhysicsLocationDp` + `setPhysicsRotationDp`，或在自维护的内嵌 Java 层补一个便利方法。
- 单精度 native 同样导出 `setPhysicsLocationDp` 等符号，因此**用 Sp native 也不会崩** —— 只是内部仍以 32 位存储，双精度入口形同虚设。这带来一个重要好处：
  **Kotlin 层可以先改用 Dp API，再从容切换 native。**

### 3.3 实测：Dp native 确实解决问题

端到端实验（同一份 Java 代码，分别加载 Sp / Dp native）：在远处放一个静态地面盒，上方放一个动态方块，跑 3 秒。

| native | 模式 | 请求 X | 实际存储 X | 位置误差 | 落地 Y |
|---|---|---|---|---|---|
| Sp | float 入口 | 1000000.300000 | 1000000.312500 | 0.0125 | 0.8000 |
| Sp | float 入口 | 30000000.300000 | 30000000.000000 | **0.3000** | 0.8000 |
| Sp | **double 入口** | 30000000.300000 | 30000000.000000 | **0.3000** | 0.8000 |
| **Dp** | **double 入口** | 30000000.300000 | **30000000.300000** | **0.0000** | 0.8000 |

两个关键点：

1. **Sp native 下走 double 入口毫无帮助** —— 数值在 native 内部仍被截断为 32 位。
   必须换 native，光改 Java/Kotlin 调用方式无效。
2. **Dp native 下 double 入口保留完整精度**（误差 0.0000），即便在 3000 万格外。

该实验的推进部分还显示：在 1.2e7 格处，float 的间距是 1 格，给刚体设 0.25 格/秒的速度后推进 60 tick，Sp native 上的 X 坐标**完全不动**（1.2e7 处的 0.25 位移无法表示），Dp native 上正常推进到 12345678.55。即：远距离的 float 存储不仅让位置偏移，还会让**低速运动整段消失**。

> 附带发现：该实验中球体在 Sp / Dp 下都正确停在地面上。因为**相对**模拟（球 vs 地面）
> 在两侧被同样平移时仍然自洽。真正坏掉的是**绝对坐标语义**：
> 碰撞点反查方块位置（`src/main/kotlin/cn/solarmoon/spark_core/physics/terrain/PhysicsChunkSection.kt#getBlockPosFromContactPoint`）、
> 渲染对齐、以及相邻地形 section 之间的相对错位。
> 这一点很重要，避免把问题描述得过于夸张。

---

## 4. 代价评估

### 4.1 性能（本机实测）

测试环境：AMD Ryzen 9 7940HX（16 核 / 32 线程）、Windows、JDK 21。
Mt 构建在 native 侧固定开 **2** 个求解线程（`../Libbulletjme/src/main/native/glue/jmeClasses.cpp#setNumThreads`，宏 `BT_THREADSAFE` 之下），因此多线程收益本身有限；`countThreads()` 对非 Mt 构件返回 1、对 Mt 构件返回 2（`../Libbulletjme/src/main/native/glue/com_jme3_bullet_util_NativeLibrary.cpp#countThreads`）。

同一份探针代码，仅切换 native，各 3 次均值（单次离散 < 1%）：

| 场景 | Sp 非 Mt（`natives/bullet`，**切换前加载的这一支**） | SpMt（`natives/bullet_sp`，切换前不在加载路径上） | DpMt（Maven `23.1.0-DpMtRelease`） |
|---|---|---|---|
| 800 动态刚体，600 tick | 1.118 ms/tick | 0.966 ms/tick | 1.114 ms/tick |
| 4000 动态刚体，300 tick | 2.525 ms/tick | 2.153 ms/tick | 2.630 ms/tick |
| `countThreads()` | 1 | 2 | 2 |

同一批数据给出两个口径：

| 口径 | 800 刚体 | 4000 刚体 | 说明 |
|---|---|---|---|
| 相对**切换前投放**（Sp 非 Mt → DpMt） | −0.4% | +4.1% | 本次采用的路径（§6 的 D2）：Dp 的代价与 Mt 的收益互相抵消后的净账 |
| 相对 **Mt**（SpMt → DpMt） | +15.2% | +22.1% | 若先把加载路径修正成 Mt、再单独评估换 Dp 的增量 |

- 复杂场景下按 **20~35%** 规划「Dp 取代 Mt」那一步（即上表第二行再留出余量）更稳妥。
- 绝对量级参考：4000 刚体 DpMt 约 2.6 ms/tick。Minecraft 单 tick 预算 50 ms，物理通常只占其中一部分。
- **归因未拆分**：Dp 使 `btScalar` 变为 `double`，变换与 AABB 存储、内存流量翻倍；同时失去 Bullet 针对 float 的 SIMD 向量化路径。本文没有区分这两者的贡献。
- **确定性**：Mt 会按上面的 `setNumThreads` 固定开 2 个求解线程，求解顺序因此不是严格可复现的（单求解线程时才是）。本项目的权威位姿与速度会定期同步到客户端（决策 D2），因此该不确定性不作为约束。
- 未测：Dp 下刚体内部存储翻倍带来的 RAM 占用（`btRigidBody` / `btCollisionObject` 内部的 `btTransform` 与 AABB 树节点均为双倍宽度）。

### 4.2 体积与投放动作

切换前投放（11 文件，37,222,568 字节）：

| 目录 | 文件数 | 字节数 | 角色 |
|---|---|---|---|
| `natives/bullet/` | 3 | 10,325,944 | Windows / Linux 的 Sp 非 Mt（实际被加载） |
| `natives/bullet_sp/windows`、`linux` | 3 | 10,356,968 | Windows / Linux 的 SpMt（不在加载路径上） |
| `natives/bullet_sp/macos` | 2 | 6,095,632 | macOS 的 Sp 非 Mt |
| `natives/bullet_sp/android` | 3 | 10,444,024 | Android 的 Sp 非 Mt |

切换后的投放（决策 D6，已落地）：11 文件 / 37,512,368 字节，**相对切换前 +289,800 字节（+0.8%）**。

| 目录 | 文件数 | 字节数 | flavor |
|---|---|---|---|
| `natives/bullet_dpmt/{windows,linux}` | 3 | 10,428,992 | DpMtRelease |
| `natives/bullet_dp/{windows,linux,macos}` | 5 | 16,639,352 | DpRelease |
| `natives/bullet_sp/android` | 3 | 10,444,024 | SpRelease（与切换前同一份文件） |

投放动作（均已执行）：

| 动作 | 路径 |
|---|---|
| 新增 | `natives/bullet_dpmt/{windows/x86_64, linux/x86_64, linux/arm64}` |
| 新增 | `natives/bullet_dp/{windows/x86_64, linux/x86_64, linux/arm64, macos/x86_64, macos/arm64}`（注意 macOS 的平台段映射，见 §3.1） |
| 保留 | `natives/bullet_sp/android/{arm64-v8a, armeabi-v7a, x86_64}` |
| 移除 | `natives/bullet/**` 与 `natives/bullet_sp/{windows,linux,macos}/**`（这两档的角色由上面两个 Dp 目录接管） |

- 总量几乎不涨的原因：切换前桌面 Sp 存在两档（`natives/bullet` 与 `natives/bullet_sp` 各 3 个文件），它们的位置由 DpMt + Dp 接管。
- 桌面与 macOS 的投放只到 Dp 档：`DpRelease` 与 `SpRelease` 的运行时依赖相同（都不依赖 OpenMP 运行时），多留一档 Sp 不增加覆盖。该变体见 §5 的方案 E。
- 单独对比同平台同 flavor 的体积：Windows `Sp` 2,345,984 → `Dp` 2,415,616（+3.0%）→ `DpMt` 2,439,168（+4.0%）。

### 4.3 平台可用性

| 平台 | Dp 构件 | 切换前投放 | 现状（决策 D2 / D3 / D6，已落地） |
|---|---|---|---|
| Windows x86_64 | Dp / DpMt | SpRelease（非 Mt） | **DpMtRelease** |
| Linux x86_64 | Dp / DpMt | SpRelease（非 Mt） | **DpMtRelease** |
| Linux ARM64 | Dp / DpMt | SpRelease（非 Mt） | **DpMtRelease** |
| **macOS x64 / ARM64** | 仅 Dp（无 DpMt） | SpRelease（非 Mt） | **DpRelease**（线程数不变） |
| **Android（3 个 ABI）** | Maven Central 未发布，可自建 | SpRelease（非 Mt） | **SpRelease**（候选表里预留 `bullet_dp`，见 §7） |
| Windows ARM64 | Dp / DpMt | 未投放 | 未投放（本次不变） |

- macOS 无 DpMt 是上游的构建限制（`../Libbulletjme/build.gradle` 中 Mt 的可构建条件含 `(os != 'osx')`），
  与 `../Libbulletjme/README.md` 中「多线程库只提供给 Windows 与 Linux 的 64 位架构」一致；
  按该条件，macOS 上不存在 `MacOSX*-DpMt*.jar` 这类构件。
  由于本仓库在 macOS 上投放的也是非 Mt 构件，**macOS 换 DpRelease 不改变线程数**，只是拿到双精度。
- Android 的 Dp **不是引擎能力缺失**：`../Libbulletjme/android.gradle` 的 `productFlavors` 同时定义 `Dp`（`cppFlags '-DBT_USE_DOUBLE_PRECISION'`）与 `Sp`，而发布流程（`../Libbulletjme/.github/workflows/push.yml`）只跑 `-Pflavor=Sp`，所以 Maven Central 只有 Sp 的 AAR。
  若要 Android Dp，需要自行构建 AAR 并把 `jni/<abi>/libbulletjme.so` 重新打包到 `natives/bullet_dp/android/<abi>/`；这是一次构建与打包工作，不是改引擎。候选表里已经预留该目录，**放进即生效，无需改代码**。
  代价提示：Android 是 32 位 `armeabi-v7a`、`arm64-v8a`、`x86_64` 三个 ABI，其中 32 位 ABI 的双精度吞吐最低；若将来补 Dp，优先只补 `arm64-v8a`。
- 本仓库已投放的 Android ABI 是 `arm64-v8a`、`armeabi-v7a`、`x86_64` 三个，`riscv64` / `x86` 未投放。

#### 本机自行构建 Dp 的可行性

若确实需要自行构建（例如为 Android 补 Dp），本机状况实测如下：

- **版本要先钉死**：`../Libbulletjme` 的工作树是 `23.1.0` 之后 17 个提交（`git describe` 为 `23.1.0-17-g02d2c2c7`，分支 `master`），但它的 `expectedVersion` 仍写着 `"23.1.0"`。直接按工作树构建会得到一个「自称 23.1.0、实际含后续代码」的 native，与 Maven 构件不是同一份代码。自建前先 `git -C ../Libbulletjme checkout 23.1.0`。
- 上游用的是 **Gradle 9.6.1**（`../Libbulletjme/gradle/wrapper/gradle-wrapper.properties`），`cpp` 插件在该版本仍可用，
  `org.gradle.model.*` 与 `CppPlugin` 均为真实实现而非 stub，`model { }` DSL 可正常求值。
- **MSVC 14.44.35207（VS 2022 Community）编译器可用**：`cl.exe`、`dumpbin.exe`、`omp.h`、
  `vcomp.lib` / `libomp.lib` 均在（`/openmp` 可链接，SpMt / DpMt 无碍），JNI 头文件也齐备。
- ⚠️ **但 Gradle 无法自动发现 VS**：`vswhere.exe` 缺失、注册表
  `HKLM\SOFTWARE\...\VisualStudio\SxS\VC7` 缺失、`cl.exe` 不在 PATH，三条发现路径全断。
  **解决办法**：先 `call "...\VC\Auxiliary\Build\vcvars64.bat"` 再运行 Gradle，实测可成功出 obj。
- ⚠️ **本机没有 MinGW / clang / gcc**：因此 Linux / ARM / macOS 目标**无法在本机交叉编译**，
  必须在对应平台或 CI 上构建。这也是「直接用上游现成构件」比自建划算的根本原因。
- **Android 自建走另一条工具链**（AGP + `ndkBuild`，见 `../Libbulletjme/Android.mk`），与 MSVC / `vswhere` 无关；但本机目前没有 Android SDK 与 NDK（`ANDROID_HOME` 为空，无 `ndk/` 目录），需要先装 SDK `platforms;android-35` 与 `../Libbulletjme/gradle/libs.versions.toml` 里钉死的 `ndk;29.0.14206865`，再 `cp android.gradle build.gradle` 后跑 `-Pflavor=Dp`。这是数 GB 的下载，且每次升级 libbulletjme 版本都要重来。

### 4.4 Kotlin 层改造量（只加不改）

决策 D1 要求**对外物理 API 只加不改**：Machine-Max 直接读写 `src/main/kotlin/cn/solarmoon/spark_core/physics/body/PhysicsBodyExtension.kt#stateOf` 返回对象上的 `getTransform` / `getLastTransform` / `setTransform` / `setLastTransform` / `getCachedBoundingBox`，并调用 `src/main/kotlin/cn/solarmoon/spark_core/physics/level/WorldSnapshot.kt#collectBodiesInAabb` 与 `src/main/kotlin/cn/solarmoon/spark_core/physics/body/ManifoldPoint.kt#getPositionWorld`。这些签名与字段类型保持不变，Dp 能力以**新字段 / 新重载**的形式加入。

坐标管线的边界规则（决策 D4）：

| 走 Dp | 保持 float |
|---|---|
| 绝对世界坐标的**写入**与**回读**：刚体位置、刚体变换、地形 section 刚体变换、附着/装置位姿、幽灵体（activator） | 速度、角速度、重力、线性/角因子、缩放、阻尼、休眠与接触阈值等小量级参数 |
| 由绝对坐标派生的**查询盒**（内部计算用） | 面向渲染与网络的 float32 类型（`Transform`、`Matrix4f`、`Vector3f` 载荷） |
| 接触点位置（新增 Dp 通路，见下） | 现有 float 通路一律保留 |

要改的位置（实际落地结果，全部为「新增」或「等价改写」，签名不变）：

| 文件（符号） | 改法 |
|---|---|
| `src/main/kotlin/cn/solarmoon/spark_core/physics/PhysicsHelper.kt#toBVector3f` | **新增** `#toVec3d`（`Vec3 → Vec3d`）与 `#toVec3`（`Vec3d → Vec3`）；`toBVector3f` 原样保留 |
| `src/main/kotlin/cn/solarmoon/spark_core/physics/PhysicsHelper.kt#absoluteAabb` | **新增**：求绝对包围盒的唯一入口，见下方「AABB 怎么算」 |
| `src/main/kotlin/cn/solarmoon/spark_core/physics/body/RigidBodyEntity.kt#setPosRaw` | 位置写入改走 `setPhysicsLocationDp(Vec3d(x, y, z))` |
| `src/main/kotlin/cn/solarmoon/spark_core/physics/body/CollisionObjectEntity.kt#baseTick` | 位置回读改走 `getPhysicsLocationDp`；`setPos` 的入参仍是 `Vec3`，实体位置本身就是 double，因此回读不再经过 float |
| `src/main/kotlin/cn/solarmoon/spark_core/physics/body/PhysicsBodyState.kt#absoluteLocation`、`#absoluteAabb` | **新增** `Vec3`（double）绝对位置与绝对包围盒；`lastTransform` / `transform` / `cachedBoundingBox`（float）原样保留给下游与渲染 |
| `src/main/kotlin/cn/solarmoon/spark_core/physics/level/WorldSnapshot.kt#syncPose` | **新增**（`#syncTransform` 与 `#createSnapshotObject` 都走它）：位置与姿态用 `getPhysicsLocationDp` / `getPhysicsRotationDp` 读出、`setPhysicsLocationDp` / `setPhysicsRotationDp` 写入；缩放仍走 float（没有 Dp 写入入口，且快照与主刚体共享 shape） |
| `src/main/kotlin/cn/solarmoon/spark_core/physics/level/PhysicsSpaceBounds.kt#PhysicsSpaceBounds` | **新增**：`PhysicsWorld` 与 `WorldSnapshot` 共用的一组世界边界常量（§6 的 D9），取代原先两处各写一份的 `±Int.MAX_VALUE.toFloat()` |
| `src/main/kotlin/cn/solarmoon/spark_core/physics/body/PhysicsBodyExtension.kt#attachToEntity` | 位置写入改走 Dp（源数据本来就是 `Vec3`） |
| `src/main/kotlin/cn/solarmoon/spark_core/physics/body/PhysicsBodyExtension.kt#attachToBone`、`#attachToLocator` | **刻意保持 float**，理由见下方「为什么骨骼附着不改」 |
| `src/main/kotlin/cn/solarmoon/spark_core/physics/terrain/PhysicsChunkSection.kt#createPhysicsBody` | section 刚体位置改 `setPhysicsLocationDp`；原式 `minBlockX() + 8f` 在 \|x\| > 2^24 后连整数都无法精确表示 |
| `src/main/kotlin/cn/solarmoon/spark_core/physics/terrain/PhysicsChunkManager.kt#onBlockUpdated` | activator 幽灵体的位置写入改走 Dp（`+0.5` 在 ≳3e7 格处会被 float 吃掉） |
| `src/main/kotlin/cn/solarmoon/spark_core/compat/create/CreateContraptionPhysicsHost.kt#onSyncTick`、`#onPhysicsTick` | 装置位姿写入改走 Dp |
| `src/main/kotlin/cn/solarmoon/spark_core/physics/level/PhysicsLevel.kt#start` | 启动日志补上 `NativeLibrary.isDoublePrecision()`（该方法此前全树零调用点） |

三点说明：

- **查询结果本次不 Dp 化**（决策 D10）：`WorldSnapshot` 的 ray / sweep 结果与 `ManifoldPoint#getPositionWorld` 仍是 float。它们在生产路径上的消费方是 Machine-Max（`CollisionHandler`）而不是 Spark-Core 自身；`ManifoldPoint#getPositionWorldDp` 已经就位，需要时由消费方按需接入。
  `src/main/kotlin/cn/solarmoon/spark_core/physics/terrain/PhysicsChunkSection.kt#getBlockPosFromContactPoint` 因此**没有**新增 `Vec3d` 重载：它在 Spark-Core 内没有调用点，加一个无人使用的重载只是死代码，等消费方需要时再加。
- **地形形状已经是 section 局部坐标**：`src/main/kotlin/cn/solarmoon/spark_core/physics/terrain/PhysicsChunkSection.kt#addComplexShape` 用 `- 8f` 把 section 中心移到原点，只有 section 刚体的 transform 是绝对值（`#createPhysicsBody`）。地形侧离「完全局部化」只差一步，但本次不做这个重构。
- **`PhysicsBodyState` 没有 `transformDp` / `boundingBoxDp`**，理由见下方两小节。

#### AABB 怎么算：原点求形状盒 + 双精度位置叠加

内嵌 Java 层**没有**双精度 AABB 入口：`src/main/java/com/jme3/bullet/collision/shapes/CollisionShape.java#getAabb` 的 JNI 签名是
`(long shapeId, Vector3f location, Matrix3f basis, Vector3f minima, Vector3f maxima)`，入参出参**全是 float**
（实现见 `../Libbulletjme/src/main/native/glue/com_jme3_bullet_collision_shapes_CollisionShape.cpp#getAabb`）。
native 侧也不存在 Dp 版符号，要新加一个就得**自建 native**，与决策 D5（用上游 23.1.0 现成构件）冲突。

因此 `src/main/kotlin/cn/solarmoon/spark_core/physics/PhysicsHelper.kt#absoluteAabb` 的做法是：

1. 把 `translation` 传 **0**、`rotation` **照传**，求形状在原点附近的 AABB —— 这时数值只有物体尺寸量级，float 完全够用；
2. 再把**双精度**的绝对位置叠加到 `min` / `max` 上。

被消除的误差是真实存在的：3.0e7 格处 float 间距为 2 格，直接传大坐标会让 native 在 float 里算出的 `min` / `max` 双双落在 2 格栅格上，**中心最多偏 2 格、连尺寸（`max − min`）本身也被量化成 2 格的整数倍**。这正是远处「地形不激活 / 掉出世界」的根源。

**为什么旋转不需要双精度**：旋转只决定「原点附近」那一段局部盒的形状，其误差贡献约为
`物体尺寸 × float 相对精度 ≈ 物体尺寸 × 6e-8`，即使 16 格大的装置也不到 1e-6 格，比碰撞 margin（0.04）和方块粒度（1.0）小四五个数量级；
而且 native 侧的 `Matrix3f` 参数本来就只接受 float，双精度姿态到了边界同样会被窄化。所以这里用 `Matrix3f`（`getPhysicsRotationMatrix`）而不是 `Matrix3d`。

对应地，`src/main/kotlin/cn/solarmoon/spark_core/physics/body/PhysicsBodyState.kt#cachedBoundingBox`（float，下游 Machine-Max 在用）**保留原来的第二次计算**，没有改成「由双精度 AABB 收窄得到」：
后者虽然省一次 `getAabb`，但会让下游拿到的 float 值与大坐标处漂移（大坐标下可能跳 2 格），且 float 字段会变成由 Dp 推导，与 D1「float 字段保留给下游」的定位不再一致。

#### 为什么骨骼附着不改：源数据本身就是 float

`src/main/kotlin/cn/solarmoon/spark_core/physics/body/PhysicsBodyExtension.kt#attachToBone` 与 `#attachToLocator` 的绝对平移量来自
`src/main/kotlin/cn/solarmoon/spark_core/animation/model/BonePose.kt#getWorldBonePivotMatrix` 与
`src/main/kotlin/cn/solarmoon/spark_core/animation/model/ModelPose.kt#getWorldBoneLocatorMatrix`，两者返回的都是 joml **`Matrix4f`**：
大坐标在矩阵里就已经被 float 量化了，再把它转成 `Vec3d` 写进去不会增加任何精度，只会多一次分配。
真正要修的是这两处矩阵本身的精度，那是另一件事。
`src/main/kotlin/cn/solarmoon/spark_core/physics/body/PhysicsBodyExtension.kt#attachToEntity` 不同 —— 它的源数据是 `entity.boundingBox.center`（`Vec3`，double），所以那一处改了 Dp。

#### 为什么 `PhysicsBodyState` 没有 `transformDp`

`src/main/java/com/jme3/math/TransformDp.java#TransformDp` 确实存在，但本次没有把它引入状态类：

- 上游**只有 `getTransformDp`，没有 `setPhysicsTransformDp`**，`TransformDp` 只能整体读、不能整体写，因此它无法替代 `syncPose` 里的三次 Dp 写入；
- 当前没有任何消费方需要「整姿态」的双精度表示：地形激活盒只需要位置，实体位置是 `Vec3`，渲染与网络要的是 float32；
- 硬要把它换掉 `Vec3d` 也不行：`TransformDp#getTranslation` 返回的 `Vec3d` 是 **JNI 的写入目标**（`getPhysicsLocationDp(result.getTranslation())`），必须可变，而 MC 的 `Vec3` 字段是 `final`；引擎层也不应依赖 `net.minecraft` 类型。

需要整姿态时，调用方直接 `body.getTransformDp(store)` 现取即可，不必包一层常驻字段。

**全局限界的一个说明**：`src/main/java/com/jme3/bullet/PhysicsSpace.java#PhysicsSpace` 的构造函数只接受 `Vector3f` 边界，没有 `Vec3d` 重载 —— 但 `PhysicsWorld` 用的是 **DBVT** broadphase，DBVT **不量化、不裁剪**世界边界；
只有 `AXIS_SWEEP_3` / `AXIS_SWEEP_3_32` 才依赖它做 sweep-and-prune 量化。
所以边界保持 float 不构成实质障碍，**但不能换成 AXIS_SWEEP**。按原版世界边界尺度声明（±29,999,984）时该值在 float 下可精确表示，声明与数值一致。

---

## 5. 备选方案对比

| 方案 | 精度 | 性能 | 平台 | 工作量 |
|---|---|---|---|---|
| **A. 换 Dp / DpMt native（本次采用）** | 桌面世界边界处误差 0；Android 维持 Sp | 相对切换前投放 −0.4% ~ +4.1% | 全平台（Android 用 Sp 共存） | Kotlin 只加不改 + 加载器按平台选候选 + 资源投放 |
| ~~B. 浮动原点 / 分岛局部化~~ | — | — | — | ❌ **已排除**（多玩家无法取原点） |
| C. 不做 | 524,288 格外 margin 失真（§1），1e7 格外碰撞明显损坏 | — | — | 0 |
| D. 同时投放 Sp + Dp 并**运行时可配置** | 取决于配置 | 取决于配置 | 全平台 | 额外复杂度；按平台固定投放（方案 A）已足够，不需要运行时开关 |
| ~~E. 在桌面与 macOS 再留一档 Sp 作末位候选~~ | 同 A | 同 A | 全平台 | ❌ **已排除**：`DpRelease` 与 `SpRelease` 的运行时依赖相同（都不依赖 OpenMP 运行时），多留一档不增加覆盖，代价是 +16,452,600 字节（+44%） |

**B 的现实困难**：地形可以天然按 section 局部化，但实体 / 布娃娃 / 抛射物是**漫游**的，
单个全局偏移无法覆盖全世界。要彻底做对，需要「按区域分岛 + 每个岛自己的原点 + 跨岛迁移」。

> ❌ **方案 B 已被决策排除**：服务端同时存在多个玩家，原点取哪里都无法自洽
> （按玩家分岛则跨岛交互、全局约束、远距离抛射物全部失效）。
> 本方案不作为候选。

---

## 6. 实施前决策（已定）

| 编号 | 决策 | 依据 / 影响 |
|---|---|---|
| **D1** | 对外物理 API **只加不改** | Machine-Max 直接读写 `PhysicsBodyExtension.kt#stateOf` 上的位姿与包围盒字段，并调用 `WorldSnapshot.kt#collectBodiesInAabb` / `ManifoldPoint.kt#getPositionWorld`；改签名会打断下游编译。Dp 一律以新字段、新重载加入 |
| **D2** | 桌面用 **DpMt** | 刚体权威位姿与速度定期同步到客户端，求解顺序的确定性不是硬需求 |
| **D3** | **客户端也切 Dp** | 客户端参与渲染与位姿回读；桌面客户端与服务端同档。Android 客户端保持 Sp |
| **D4** | 坐标管线边界见 §4.4 的表 | 绝对世界坐标走 Dp；小量级参数与 float32 渲染/网络类型保持 float |
| **D5** | native 版本统一 **23.1.0** | 与内嵌 Java 层的 `NativeLibrary.java#expectedVersion` 一致。自建必须先切到该 tag（工作树是 23.1.0 之后 17 个提交，但版本号仍写 23.1.0，靠版本号分辨不出） |
| **D6** | 投放为**按平台解析的候选列表**：Windows / Linux = `bullet_dpmt` → `bullet_dp`；macOS = `bullet_dp`；Android = `bullet_dp` → `bullet_sp` | 桌面拿 DpMt，macOS 只有 Dp（无 DpMt 构件），Android 当前只有 Sp。Android 的 `bullet_dp` 目录留空，将来放入 `.so` 即自动生效 |
| **D7** | **重构 `NativeLoader`**：候选列表取代「主库 + 单一回退」 | 原设计无法表达「按平台首选不同 flavor」；详见 §7 |
| **D8** | 实际加载的档**弱于该平台实际投放的最强档**时只警告，不阻断启动 | 例：桌面从 `bullet_dpmt` 落到 `bullet_dp`；将来 Android 有了 `bullet_dp` 却落到 `bullet_sp`。平台实际投放的最强档本身就是 Sp 时（当前 Android）只记 info。判定方式与实测实例见 §7.3 第 4 条。只有「全部候选都加载失败」才抛 `ModLoadingException` |
| **D9** | 世界边界按**原版世界边界尺度**：X/Z `±29,999,984`，Y 保持 `-1,000..15,000` | 声明性；DBVT 不裁剪。±29,999,984 在 float 下可精确表示。Y 的下界远低于原版建筑下限，用于覆盖自定义维度 |
| **D10** | 容差保持现状；查询结果（ray / sweep / 接触点）**本次不 Dp 化** | 接触点回退查方块这条链需按 §10 的手工场景复测一次 |
| **D11** | 验收判据：① 启动日志断言 `isDoublePrecision`；② 手工场景（远处 F3+B 观察调试盒）；③ 可选：为桌面模块加一个 JUnit 探针 | ③ 已实施，见 §11：`src/test/kotlin/cn/solarmoon/spark_core/physics/DoublePrecisionNativeTest.kt#DoublePrecisionNativeTest`（单元测试环境能加载 native，因此探针可跑）。① 已实施，见 `src/main/kotlin/cn/solarmoon/spark_core/physics/level/PhysicsLevel.kt#start` 的 `doublePrecision:` 字段。② 仍需人工在游戏内执行 |

目标布局按 §4.2 的表执行：桌面与 macOS 只投 Dp 档，Android 保持 Sp。若将来出现「两个 Dp 档都加载失败」的机型，再按 §5 的方案 E 补回 Sp 档即可（只加资源，不动代码）。

### 与下游模组的关系

本次切换不改任何对外签名（D1），也不改网络协议；依赖 Spark-Core 的模组（ARMS-Core、Machine-Max）不需要为此次切换改代码。它们的物理行为差异只来自引擎本身：Android 客户端与服务端在 ≳1e5 格外会出现精度不对称（客户端 Sp、服务端 Dp），这是本次唯一有意接受的不一致。

---

## 7. 混合投放与 `NativeLoader` 重构

### 7.1 原理：Dp 入口在所有 flavor 上都存在

`Dp` 系列 native 方法**不是**条件编译的，它们存在于**所有** flavor 中：

- `../Libbulletjme/src/main/native/glue/jmeBulletUtil.cpp#convertDp` 读取 `Vec3d` 的 `double` 字段后调用
  `btVector3::setValue(x, y, z)`；在 Sp 构建中 `btScalar` 是 `float`，于是**隐式窄化**。
  **没有任何 `#ifdef BT_USE_DOUBLE_PRECISION` 守卫**。
- 实测所有已投放的 native（含 Android 3 个 ABI、macOS 2 个架构）**都含完整的 Dp 符号字符串**：
  `setPhysicsLocationDp` / `getLocationDp` / `setLinearVelocityDp` / `getLinearVelocityDp` /
  `setPhysicsRotationDp` / `isDoublePrecision` 全部命中。

因此 Sp native 上调用 Dp 入口**不会 `UnsatisfiedLinkError`，不会崩**，只是精度退化为 float。这就是「同一份代码同时跑 Dp 与 Sp」的前提。

### 7.2 实测（同一份 .class 文件，不做任何分支）

| | Sp native（≈Android） | Dp native（桌面） |
|---|---|---|
| `isDoublePrecision` | false | true |
| `setPhysicsLocationDp(12345678.3)` 存储值 | 12345678.000000 | **12345678.300000** |
| 位置误差 | **0.300000** | **0.000000** |
| `setLinearVelocityDp` | ✅ 正常 | ✅ 正常 |
| `setPhysicsRotationDp` | ✅ 正常 | ✅ 正常 |
| `getPhysicsLocation`（float 入口，同一刚体） | ✅ 正常 | ✅ 正常 |
| 推进 60 tick | ✅ 正常 | ✅ 正常 |

两个 native 上**都没有链接错误、没有异常**，float 与 Dp 入口可以并存于同一刚体。

### 7.3 目标设计

`NativeLoader` 从「一个主库 + 一个回退」改为**按平台解析出的候选列表**，逐个尝试：

| 平台 | 候选顺序 |
|---|---|
| Windows / Linux | `bullet_dpmt` → `bullet_dp` |
| macOS | `bullet_dp` |
| Android | `bullet_dp`（当前未投放，将来放入 `.so` 即自动生效）→ `bullet_sp` |

投放目录（与候选名一一对应，文件数与字节数见 §4.2）：

```
natives/bullet_dpmt/{windows,linux}/<arch>/     ← DpMtRelease
natives/bullet_dp/{windows,linux,macos}/<arch>/ ← DpRelease
natives/bullet_sp/android/<arch>/               ← SpRelease（与切换前同一份文件）
```

行为规格（与实现一致）：

1. **资源不存在** → 记一条 info 级日志后跳到下一个候选（macOS / Android 上这是常态；不会像 §2.2 描述的那样打印错误块）。
2. **资源存在但 `System.load` 失败**（`UnsatisfiedLinkError`）→ 走现有的 Linux 依赖诊断（`NativeLoader.kt#buildLoadErrorReport`），记 error 后继续下一个候选。
3. **加载成功** → 立刻用 JNI 查实际 flavor（`NativeLibrary.isDoublePrecision()` / `isThreadSafe()` / `countThreads()` / `isDebug()`）并打一行汇总，实测输出形如
   `物理 native: bullet_dpmt（Dp + Mt，2 求解线程，Debug:false）← C:\...\spark_core_natives\spark_core\bullet_dpmt\windows\x86_64\bulletjme.dll`。
   **以实测 flavor 为准，不以模块名推断**——投放目录与 flavor 不一致的问题（§2.2）正是靠实测发现的。
4. **降级只警告**（决策 D8）：判据不是「候选表第 0 项」，而是「**本平台实际投放的最强候选**」——
   逐个候选查资源存在性，第一个存在的候选就是该平台的期望档，若最终落点比它弱则 `warn` 并写出后果，
   否则只记 info。这样 Android（`bullet_dp` 目录不存在，实际投放的最强档就是 `bullet_sp`）落到 Sp 时**不会**误报降级，
   而桌面从 `bullet_dpmt` 落到 `bullet_dp` 时会明确提示「求解线程数下降」。
5. **全部候选失败** → 抛 `ModLoadingException`，消息里带上每个候选各自的失败原因；
   一个候选都没投放时（例如未投放的 Windows ARM64）单独报「没有任何已投放的候选档」并列出尝试过的路径。
6. 去掉 `load(modId, moduleName, fallbackModuleName)` 的三参形态，改由平台解析候选列表；`NativeLoader` 在 Spark-Core 之外没有调用点（在 ARMS-Core 与 Machine-Max 中检索 `NativeLoader` 无命中）。
7. 提取缓存目录已经按模块名分桶（`spark_core_natives/<modId>/<module>/<platform>/<arch>/`），模块名带上 flavor 后，同名模块换二进制导致命中旧缓存的风险随之消失。
8. macOS 的平台段需要从构件的 `osx` 映射到加载器的 `macos`（§3.1 的打包陷阱）。这是**投放**时的事，加载器只认 `macos`。
9. ⚠ **`IModFile#findResource` 不能当作存在性检查**：对**目录形态**的模组（开发环境 `build/resources/main`、
   `:test` 任务）它只做路径拼接、不校验文件是否存在，缺失时返回一个指向不存在文件的 `Path`。
   实测后果：把 `natives/bullet_dpmt` 整个移走后，加载器会「找到」一个并不存在的资源，然后在解压阶段失败，
   于是本该是「未投放，跳过」的 info 变成了「候选加载失败」的 error 块 + 一条**假的降级警告**。
   因此 `src/main/kotlin/cn/solarmoon/spark_core/pack/NativeLoader.kt#findResourceOrNull` 除了调用 `findResource`
   还必须自己 `Files.isRegularFile` 确认一次（jar 形态的模组不受影响，但加上这一步让开发环境与生产行为一致）。

### 7.4 复用临时对象，避免每帧分配

`getPhysicsLocationDp(null)` 会 `new Vec3d()`，`getAabb` 路径还会新建 `Vector3f` / `BoundingBox`。
落地时在每个刚体上缓存了可复用的 `Vec3d` / `Matrix3f` / `BoundingBox` / `Vector3f`
（见 `src/main/kotlin/cn/solarmoon/spark_core/physics/body/PhysicsBodyState.kt#absoluteAabb`、
`src/main/kotlin/cn/solarmoon/spark_core/physics/level/WorldSnapshot.kt#syncPose`、
`src/main/kotlin/cn/solarmoon/spark_core/physics/body/CollisionObjectEntity.kt#baseTick`），
只有对外发布的 `Vec3` 位置是每 tick 新建的（它不可变，必须作为快照发布）。

JNI 调用次数与原实现同量级：`getTransform` + `setPhysicsTransform` 本来就是 6 次 native 调用
（各 3 次：位置 / 姿态 / 缩放），`syncPose` 用 Dp 读 + Dp 写 + float 缩放同样是 6 次；
`src/main/kotlin/cn/solarmoon/spark_core/physics/body/PhysicsBodyState.kt#update` 新增 1 次 `getPhysicsLocationDp`，
`src/main/kotlin/cn/solarmoon/spark_core/physics/body/PhysicsBodyState.kt#absoluteAabb` 按需再新增 2 次
（`getPhysicsRotationMatrix` + `getAabb`），且只在需要绝对包围盒时调用。

---

## 8. 落地步骤（已按此执行）

> **顺序上的一处修正**：原计划把「重构 `NativeLoader`」放在第 0 步「低风险清理」里，但候选表一旦生效、
> 而资源还是旧的 `natives/bullet` / `natives/bullet_sp`，模组会在启动时直接失败 ——
> **加载器重构与 native 投放必须同一个提交落地**。实际执行顺序是：
> 先做第 0 步里与加载器无关的两项（世界边界常量、启动日志）与第 1 步（Kotlin 接入 Dp，
> 此阶段仍跑在旧 Sp native 上，行为不变），最后把加载器重构与资源投放一起做完。

0. **低风险清理**：
   - 把 `src/main/kotlin/cn/solarmoon/spark_core/physics/level/PhysicsWorld.kt#PhysicsWorld` 与 `src/main/kotlin/cn/solarmoon/spark_core/physics/level/WorldSnapshot.kt#WorldSnapshot` 的边界改成决策 D9 的常量（`±29,999,984` / `-1,000..15,000`），并收敛到 `src/main/kotlin/cn/solarmoon/spark_core/physics/level/PhysicsSpaceBounds.kt#PhysicsSpaceBounds` 一处声明。
   - 在 `src/main/kotlin/cn/solarmoon/spark_core/physics/level/PhysicsLevel.kt#start` 已有的启动日志里补上 `NativeLibrary.isDoublePrecision()`。
1. **阶段一：Kotlin 层接入 Dp，native 不变。**
   按 §4.4 的表**只加不改**地接入 Dp 字段与 Dp 重载。此阶段**行为不变**（Sp native 下 Dp 入口被截断），坐标管线全部就位，可编译、可测试、可回滚。
2. **阶段二：重构 `NativeLoader` + 投放 native（同一提交）。**
   - 重构 `src/main/kotlin/cn/solarmoon/spark_core/pack/NativeLoader.kt#load`：候选列表 + 实测 flavor 日志 + 弱于实际投放最强档时只警告（§7.3），并修正 KDoc 与实际 flavor 不一致的描述。
   - 按 §4.2 的投放动作新增 `bullet_dpmt` / `bullet_dp`、移除桌面与 macOS 的 Sp 档、保留 `bullet_sp/android`；注意 macOS 的 `osx` → `macos` 映射。
   - 回退只需要动两处：`natives/bullet_dpmt` 与 `natives/bullet_dp` 里的二进制（换成对应的 Sp 构件）与 `NativeLoader` 的候选表一行。阶段一接入的 Dp 调用可以原样保留——Sp native 下 Dp 入口只是被截断，行为与切换前一致。
3. **验收**（决策 D11，执行结果见 §11）：
   - 启动日志出现 `物理 native: bullet_dpmt（Dp + Mt，2 求解线程…）`，且 `PhysicsLevel` 的启动日志里 `doublePrecision:true`；
   - 手工场景（**尚未执行，需要人在游戏内操作**）：`/tp` 到 1.0e7 格后开 F3+B，观察 `src/main/kotlin/cn/solarmoon/spark_core/visual_effect/shape/ShapeRenderer.kt#ShapeRenderer` 画出的物理调试盒是否还落在 2 格栅格上（它用 `poseStack.translate(-camPos)` 在 double 里做相机减法，刚体位姿是 float，正好能把量化暴露出来）；
   - 同场景下实测一次「接触点回退查方块」（`CreateContraptionPhysicsHost.kt#getContactBlockPosByWorldPoint`），确认 D10 的容差保持现状是可接受的。

### 不建议

- ~~浮动原点~~ —— 多玩家服务端无法取原点（见 §5 排除说明）。
- ~~同时投放 Sp + Dp 并做运行时开关~~ —— 按平台固定投放已足够，运行时开关会引入两套语义分叉。

---

## 9. 关键文件索引

| 内容 | 位置（活引用） |
|---|---|
| native 加载与候选表 | `src/main/kotlin/cn/solarmoon/spark_core/pack/NativeLoader.kt#load` |
| 平台候选表 | `src/main/kotlin/cn/solarmoon/spark_core/pack/NativeLoader.kt#candidatesFor` |
| 加载成功后的实测档位日志与降级警告 | `src/main/kotlin/cn/solarmoon/spark_core/pack/NativeLoader.kt#reportLoaded` |
| 资源存在性检查（目录形态模组的坑） | `src/main/kotlin/cn/solarmoon/spark_core/pack/NativeLoader.kt#findResourceOrNull` |
| 加载失败诊断 | `src/main/kotlin/cn/solarmoon/spark_core/pack/NativeLoader.kt#buildLoadErrorReport` |
| 平台路径映射 | `src/main/kotlin/cn/solarmoon/spark_core/pack/NativeLoader.kt#getPlatformPath` |
| 提取缓存 | `src/main/kotlin/cn/solarmoon/spark_core/pack/NativeLoader.kt#extractAndPrepareLibrary` |
| 加载调用点 | `src/main/kotlin/cn/solarmoon/spark_core/SparkCore.kt#SparkCore` |
| 启动日志（线程数 / threadSafe / Debug / doublePrecision） | `src/main/kotlin/cn/solarmoon/spark_core/physics/level/PhysicsLevel.kt#start` |
| 世界边界常量（唯一声明处） | `src/main/kotlin/cn/solarmoon/spark_core/physics/level/PhysicsSpaceBounds.kt#PhysicsSpaceBounds` |
| 物理世界边界 | `src/main/kotlin/cn/solarmoon/spark_core/physics/level/PhysicsWorld.kt#PhysicsWorld` |
| 快照世界边界与查询 | `src/main/kotlin/cn/solarmoon/spark_core/physics/level/WorldSnapshot.kt#WorldSnapshot` |
| 快照世界位姿复制（Dp） | `src/main/kotlin/cn/solarmoon/spark_core/physics/level/WorldSnapshot.kt#syncPose` |
| float↔double 转换 | `src/main/kotlin/cn/solarmoon/spark_core/physics/PhysicsHelper.kt#toVec3d`、`src/main/kotlin/cn/solarmoon/spark_core/physics/PhysicsHelper.kt#toVec3` |
| 双精度绝对包围盒 | `src/main/kotlin/cn/solarmoon/spark_core/physics/PhysicsHelper.kt#absoluteAabb` |
| 主线程位姿回读缓存（float，下游在用） | `src/main/kotlin/cn/solarmoon/spark_core/physics/body/PhysicsBodyState.kt#transform` |
| 双精度绝对位置与包围盒 | `src/main/kotlin/cn/solarmoon/spark_core/physics/body/PhysicsBodyState.kt#absoluteLocation`、`src/main/kotlin/cn/solarmoon/spark_core/physics/body/PhysicsBodyState.kt#absoluteAabb` |
| 下游共享的状态访问点 | `src/main/kotlin/cn/solarmoon/spark_core/physics/body/PhysicsBodyExtension.kt#stateOf` |
| 骨骼 / 装置 / 实体附着 | `src/main/kotlin/cn/solarmoon/spark_core/physics/body/PhysicsBodyExtension.kt#attachToBone`、`#attachToLocator`、`#attachToEntity` |
| 地形 section 局部坐标 | `src/main/kotlin/cn/solarmoon/spark_core/physics/terrain/PhysicsChunkSection.kt#addComplexShape` |
| 地形 section 绝对变换 | `src/main/kotlin/cn/solarmoon/spark_core/physics/terrain/PhysicsChunkSection.kt#createPhysicsBody` |
| 地形激活幽灵体 | `src/main/kotlin/cn/solarmoon/spark_core/physics/terrain/PhysicsChunkManager.kt#onBlockUpdated` |
| 接触点反查方块（仍是 float，见 D10） | `src/main/kotlin/cn/solarmoon/spark_core/physics/terrain/PhysicsChunkSection.kt#getBlockPosFromContactPoint` |
| 物理调试盒渲染（手工验收场景） | `src/main/kotlin/cn/solarmoon/spark_core/visual_effect/shape/ShapeRenderer.kt#ShapeRenderer` |
| Dp API 声明 | `src/main/java/com/jme3/bullet/collision/PhysicsCollisionObject.java#getPhysicsLocationDp`、`src/main/java/com/jme3/bullet/objects/PhysicsRigidBody.java#setPhysicsLocationDp` |
| Dp 姿态与整变换声明 | `src/main/java/com/jme3/bullet/collision/PhysicsCollisionObject.java#getTransformDp`、`src/main/java/com/jme3/math/TransformDp.java#TransformDp` |
| AABB 的 JNI 签名（全是 float，无法 Dp） | `src/main/java/com/jme3/bullet/collision/shapes/CollisionShape.java#getAabb` |
| 写侧缺口（无 Dp 版本） | `src/main/java/com/jme3/bullet/objects/PhysicsRigidBody.java#setPhysicsTransform` |
| 精度查询 | `src/main/java/com/jme3/bullet/util/NativeLibrary.java#isDoublePrecision` |
| 期望版本号 | `src/main/java/com/jme3/bullet/util/NativeLibrary.java#expectedVersion` |
| 双精度探针（JUnit） | `src/test/kotlin/cn/solarmoon/spark_core/physics/DoublePrecisionNativeTest.kt#DoublePrecisionNativeTest` |
| 独立探针与基准（非源码） | `tools/dp-probe/README.md` |
| 上游 flavor 定义 | `../Libbulletjme/build.gradle` |
| 上游 Android 构建 | `../Libbulletjme/android.gradle` |
| 上游发布矩阵 | `../Libbulletjme/.github/workflows/push.yml` |
| 求解线程数硬编码 | `../Libbulletjme/src/main/native/glue/jmeClasses.cpp#setNumThreads` |
| 单求解器 vs 多求解器分支 | `../Libbulletjme/src/main/native/glue/jmePhysicsSpace.cpp#createMultiThreadedSpace` |

## 10. 复现实验

评估与回归用的探针与基准已经搬进受版本管理的 `tools/dp-probe/`（它们**不是**模组源码，不参与编译；
构建与运行方式见该目录的 `README.md`）：

- `tools/dp-probe/stub/com/jme3/bullet/util/NativeLibrary.java` —— 手写的同名桩类，排在最前面时顶掉真实实现，
  可在**不构建项目**的情况下打印 `versionNumber` / `isDoublePrecision` / `isThreadSafe` / `isDebug` / `isQuickprof`。
  在 Mt 构件上加 `countThreads()` 会回调 `com/jme3/bullet/CollisionSpace`，缺完整 classpath 时抛 `NoClassDefFoundError`。
- `tools/dp-probe/DistantTest.java` —— 远处落地精度对比（§3.3）。
- `tools/dp-probe/MixedModeTest.java` —— 同一份 class 在 Dp / Sp 两种 native 上跑 Dp + float 入口（§7.1、§7.2）。
- `tools/dp-probe/BenchTest.java`（800 刚体）/ `tools/dp-probe/BenchBig.java`（4000 刚体）—— 吞吐对比（§4.1）。

运行方式（class 文件为 major 65，需 JDK 21；classpath 只需要项目自己的两份输出）：

```powershell
$java = "$env:JAVA_HOME\bin\java.exe"
$javac = "$env:JAVA_HOME\bin\javac.exe"
.\gradlew.bat classes                                  # 生成 build\classes\{java,kotlin}\main
$cp = "build\classes\java\main;build\classes\kotlin\main"
New-Item -ItemType Directory -Force build\dp-probe-out | Out-Null
& $javac -encoding UTF-8 -cp $cp -d build\dp-probe-out tools\dp-probe\*.java
$cp = "build\dp-probe-out;$cp"

$dpmt = (Resolve-Path src\main\resources\natives\bullet_dpmt\windows\x86_64\bulletjme.dll).Path
$dp   = (Resolve-Path src\main\resources\natives\bullet_dp\windows\x86_64\bulletjme.dll).Path

# flavor + 精度（前两行即 isDoublePrecision / isThreadSafe）
& $java -cp $cp MixedModeTest $dpmt
& $java -cp $cp MixedModeTest $dp

# 远距离精度对比（§3.3）
& $java -cp $cp DistantTest $dpmt

# 吞吐（§4.1），每个 native 各跑 3 次取均值
& $java -cp $cp BenchTest $dpmt
& $java -cp $cp BenchBig  $dpmt
```

> 评估当时用过的 Sp / SpMt 与 Maven 构件路径已经不在了（桌面 Sp 档已从仓库移除）；
> 需要时按 `tools/dp-probe/README.md` 的坐标从 Maven Central 重新取，
> 或直接加载 `natives/bullet_sp/android/<abi>/libbulletjme.so` 观察 Sp 行为。

非 Windows 平台（以及 macOS / Android 的 flavor 判定）用 OpenMP 运行时依赖扫描代替 JNI 实测：

```powershell
# 含 libgomp / VCOMP / omp_get_max_threads 的构件是 Mt；其余是非 Mt
Get-ChildItem -Recurse -File src\main\resources\natives | ForEach-Object {
  $t = [System.Text.Encoding]::ASCII.GetString([System.IO.File]::ReadAllBytes($_.FullName))
  $hit = @('libgomp','VCOMP','GOMP_parallel','omp_get_max_threads') | Where-Object { $t.Contains($_) }
  "{0,-52} {1}" -f $_.FullName.Replace((Get-Location).Path + '\',''), ($hit -join ',')
}
```

§3.1 的构件清单与字节数（含 macOS 的平台段是 `osx`）由下面的脚本复核，jar 与解出的 native 都可以随时删掉：

```powershell
$base = 'https://repo1.maven.org/maven2/com/github/stephengold'
$arts = @(
 'Libbulletjme-Windows64-23.1.0-DpRelease.jar','Libbulletjme-Windows64-23.1.0-DpMtRelease.jar',
 'Libbulletjme-Linux64-23.1.0-DpRelease.jar','Libbulletjme-Linux64-23.1.0-DpMtRelease.jar',
 'Libbulletjme-Linux_ARM64-23.1.0-DpRelease.jar','Libbulletjme-Linux_ARM64-23.1.0-DpMtRelease.jar',
 'Libbulletjme-MacOSX64-23.1.0-DpRelease.jar','Libbulletjme-MacOSX_ARM64-23.1.0-DpRelease.jar')
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($f in $arts) {
  $out = Join-Path $env:TEMP $f
  if (-not (Test-Path $out)) { Invoke-WebRequest "$base/$($f -replace '-23\.1\.0-.*$','')/23.1.0/$f" -OutFile $out }
  $zip = [System.IO.Compression.ZipFile]::OpenRead($out)
  $zip.Entries | Where-Object { $_.FullName -like '*bulletjme*' } |
    ForEach-Object { "{0,-58} {1,-44} {2,9}" -f $f, $_.FullName, $_.Length }
  $zip.Dispose()
}
```

内嵌 Java 层与上游的同名同数量、以及 `native` 方法名集合一致性（§3.2）：

```powershell
# 基线固定为 23.1.0 tag（工作树可能已在其之后）
$zip = "$env:TEMP\lbj-23.1.0.zip"
git -C ..\Libbulletjme archive --format=zip -o $zip 23.1.0 src/main/java
Expand-Archive -Path $zip -DestinationPath "$env:TEMP\lbj-23.1.0" -Force

# 两侧 native 方法名集合：剔除注释后按 native 声明提取方法名，再取差集
function Get-NativeNames($base) {
  $set = @{}
  Get-ChildItem -Recurse -File $base -Filter *.java | ForEach-Object {
    $t = [regex]::Replace((Get-Content -Raw $_.FullName), '/\*.*?\*/', '', 'Singleline')
    $t = [regex]::Replace($t, '//[^\n]*', '')
    foreach ($m in [regex]::Matches($t, '\bnative\b[^;{()]*?([A-Za-z_$][A-Za-z0-9_$]*)\s*\(')) {
      $set[$m.Groups[1].Value] = $true
    }
  }
  $set.Keys
}
$local = Get-NativeNames (Resolve-Path src\main\java).Path
$up    = Get-NativeNames "$env:TEMP\lbj-23.1.0\src\main\java"
"local=$($local.Count) upstream=$($up.Count)"
"仅本地: " + (($local | Where-Object { $_ -notin $up }) -join ',')
"仅上游: " + (($up    | Where-Object { $_ -notin $local }) -join ',')
```

逐文件比对文本差异时先归一化行尾（本地多数文件比上游多一个行尾空行）；代码级差异的判定口径是「剔除 javadoc 与 import 行后仍有差异」。

启动横幅（`Dp_` / `Mt_` / `Quickprof_` / `Debug_` 前缀）由 `../Libbulletjme/src/main/native/glue/jmeClasses.cpp#BT_USE_DOUBLE_PRECISION` 处的宏在 `printf` 中拼接，可直接作为 flavor 的旁证。

---

## 11. 实施记录与验证

### 11.1 实际落地内容

| 类别 | 内容 |
|---|---|
| 加载器 | `src/main/kotlin/cn/solarmoon/spark_core/pack/NativeLoader.kt#load` 重构为平台候选表：新增 `#candidatesFor` / `#findResourceOrNull` / `#reportLoaded` / `#describeTier` / `#degradationHint` 五个私有方法并改为单参形态，`#tryLoadModule` 与 `#extractAndPrepareLibrary` 改为接收已定位的 `Path`，解压失败时带上底层原因 |
| 坐标管线 | §4.4 的表（已按实际落地结果修订） |
| 常量与日志 | 新增 `src/main/kotlin/cn/solarmoon/spark_core/physics/level/PhysicsSpaceBounds.kt#PhysicsSpaceBounds`；`PhysicsLevel.kt#start` 的启动日志补 `doublePrecision:` |
| native 投放 | 新增 `natives/bullet_dpmt`（3 文件 / 10,428,992 B）与 `natives/bullet_dp`（5 文件 / 16,639,352 B）；删除 `natives/bullet` 与 `natives/bullet_sp/{windows,linux,macos}`；保留 `natives/bullet_sp/android`（3 文件 / 10,444,024 B）。全部来自 Maven Central `23.1.0` 构件，与 §4.2 的目标布局逐字节一致 |
| 测试 | 新增 `src/test/kotlin/cn/solarmoon/spark_core/physics/DoublePrecisionNativeTest.kt#DoublePrecisionNativeTest`，5 个用例 |
| 探针 | 从被 gitignore 的 `build/` 搬进受版本管理的 `tools/dp-probe/`（4 个探针 + 桩类 + `README.md`） |

### 11.2 与本文计划的差异（4 处）

1. **没有 `transformDp` / `boundingBoxDp`**，改为 `src/main/kotlin/cn/solarmoon/spark_core/physics/body/PhysicsBodyState.kt#absoluteLocation` 与 `src/main/kotlin/cn/solarmoon/spark_core/physics/body/PhysicsBodyState.kt#absoluteAabb`（理由见 §4.4 的两个小节：没有 Dp AABB 入口、没有整姿态消费方、`TransformDp` 不可写且其 `Vec3d` 不能被 MC `Vec3` 取代）。
2. **`getBlockPosFromContactPoint` 没有新增 `Vec3d` 重载** —— 它在 Spark-Core 内没有调用点，加了是死代码；D10 已经把接触点查询推迟到消费方按需接入。
3. **`attachToBone` / `attachToLocator` 保持 float** —— 源矩阵是 joml `Matrix4f`，大坐标在那一步就已经被量化，转 Dp 写入零收益。
4. **步骤顺序**：加载器重构不能单独先落地（候选表生效而资源没换会直接启动失败），必须与 native 投放同一个提交；详见 §8 开头的说明。

### 11.3 实施中发现的新问题（已修）

`IModFile#findResource` 对**目录形态**的模组不校验存在性 —— 详见 §7.3 第 9 条。
这是实测出来的，不是推断：只依赖 `findResource` 判存在性时，把 `natives/bullet_dpmt` 整个移走会让加载器
把「未投放，跳过」误报成「候选加载失败」，并附带一条假的降级警告。
修法是在 `src/main/kotlin/cn/solarmoon/spark_core/pack/NativeLoader.kt#findResourceOrNull` 里补一次 `Files.isRegularFile`。

同时顺带修掉了两个与 Dp 切换无关的诊断缺陷：解压失败的异常消息丢掉了底层原因（只报目标路径），
以及 `tools/dp-probe` 里混进的一份无法工作的 `NativeProbe.java`（`native` 方法声明在 `NativeProbe` 类上，
JNI 名字与库导出的 `Java_com_jme3_bullet_util_NativeLibrary_*` 对不上，`System.load` 后必然
`UnsatisfiedLinkError`）；可用的入口是桩类自己的 `main`，那份残留文件已删除。

### 11.4 验证证据

**（a）单元测试全绿**：`.\gradlew.bat test` 通过，88 个用例 0 失败（新增 5 个）。
新增用例覆盖：native 版本与 `expectedVersion` 一致、桌面实测为双精度、`setPhysicsLocationDp` 在 1.2e7 / −3e7 处完整往返、
绝对包围盒在世界边界坐标处中心不偏移且尺寸不被量化、以及**按真实世界参数**（DBVT + `countThreads()` 个求解器 + 同一组边界）
构造的物理空间能让 29,999,984.5 格处的刚体正常落地且横向坐标不漂移。

**（b）加载器三条路径实测**（在 `:test` 任务的模组加载阶段观察 `build/minecraft-junit/logs/latest.log`）：

| 场景 | 构造方式 | 观测到的日志 | 结果 |
|---|---|---|---|
| 正常 | 现状 | `物理 native: bullet_dpmt（Dp + Mt，2 求解线程，Debug:false）← …\bullet_dpmt\windows\x86_64\bulletjme.dll` | 最强投放档落点，info |
| 候选未投放 | 移走 `natives/bullet_dpmt` | `本平台未投放候选档 bullet_dpmt（natives/bullet_dpmt/windows/x86_64/bulletjme.dll），跳过` 后接 `物理 native: bullet_dp（Dp + 非Mt，1 求解线程…）` | **无降级警告**（实际投放的最强档就是 `bullet_dp`），与 Android 的预期语义一致 |
| 候选损坏 | 把 `bullet_dpmt` 的 dll 截断成 4 KB | error 块 + 依赖诊断 + `物理 native 降级：本平台最强投放档 bullet_dpmt（双精度 + 多线程求解）未生效，实际加载 bullet_dp（双精度、单求解线程）。本次不阻断启动。影响：求解线程数下降（高负载时物理可能降频）` | 只警告，启动不阻断 |
| 全部失败 | 两个候选的 dll 都截断 | `物理库加载失败：本平台的全部候选档（bullet_dpmt → bullet_dp）均无法加载。` + 每个候选各自的原因 | 抛 `ModLoadingException`，`:test` 以 exit 1 失败 |

**（c）投放的 native 本身**（`tools/dp-probe/MixedModeTest`，同一份 class）：

| native | 横幅 | `isDoublePrecision` | `isThreadSafe` | `setPhysicsLocationDp(12345678.3)` 往返误差 |
|---|---|---|---|---|
| `natives/bullet_dpmt/windows/x86_64` | `Dp_Mt_Libbulletjme version 23.1.0` | true | true | 0.000000 |
| `natives/bullet_dp/windows/x86_64` | `Dp_Libbulletjme version 23.1.0` | true | false | 0.000000 |

`DistantTest` 在两个 native 上的 double 行均为 `posErr 0.0000`（float 行仍是 0.3000 @ 3e7，符合预期：
float 入口的入参本身就是 float），推进 60 tick 后 X 从 12345678.300000 变为 12345678.550000 ——
即 §3.3 里「1.2e7 处低速运动整段消失」的问题在切换后消失。

**（d）验收判据的完成度**（对应决策 D11）：

| 判据 | 状态 |
|---|---|
| ① 启动日志断言 | 加载器那行（档位 + 求解线程数）与 `isDoublePrecision` 已在真实模组加载阶段实测；`PhysicsLevel.kt#start` 的 `doublePrecision:true` 需要真实世界跑起来才会打印，**尚未在游戏内观察** |
| ② 远处 F3+B 手工场景 | **未执行**，需要人工操作 |
| ③ JUnit 探针 | 已完成，见 11.4（a） |

### 11.5 剩余的已知代价与回退方式

- **新增运行时依赖**：桌面首选档 `bullet_dpmt` 需要 OpenMP 运行时（Windows `vcomp140.dll`，Linux `libgomp`）。
  切换前桌面加载的是非 Mt 的 `natives/bullet`，**不需要**它。缺失时加载失败会落到 `bullet_dp`（不需要 OpenMP）并打出降级警告，不会阻断启动。
- **取舍**：这与 §4.1 的性能账是同一件事——Dp 的代价与 Mt 的收益互相抵消（相对切换前 −0.4% ~ +4.1%）。
- **回退**：把 `natives/bullet_dpmt` 与 `natives/bullet_dp` 里的二进制换成对应的 Sp 构件（或删掉其中一档），必要时代候表改一行。
  阶段一接入的 Dp 调用可以原样保留 —— Sp native 下 Dp 入口只是被截断，行为与切换前一致（§7.1、§7.2）。
- **尚未观测**：Dp 下刚体内部存储翻倍带来的 RAM 占用（§4.1 的「未测」项），以及真机上的长时间稳定性。
