# Libbulletjme compound 接触容差补丁

`src/main/resources/natives/bullet_dpmt/windows/x86_64/bulletjme.dll` 由本目录的补丁与
脚本从 Libbulletjme 源码重建；`natives/` 下其余十个二进制是上游发布的出厂件。

| 项 | 值 |
| --- | --- |
| 上游 | `https://github.com/stephengold/Libbulletjme` |
| 提交 | `78b76008`（版本 23.1.2） |
| flavor | DpMt（`BT_USE_DOUBLE_PRECISION` + `BT_THREADSAFE` + `BT_USE_OPENMP`） |
| 配置 | Release，静态 CRT（`/MT`） |
| 目标 | Windows x86_64 |
| 产物 | `src/main/resources/natives/bullet_dpmt/windows/x86_64/bulletjme.dll` |
| 字节数 | 2,428,416 |
| SHA256 | `0FD8884255BD930DE0DBD2A24368FE6A9F32ABA5A4F0BF48D41C023902B0B0EC` |

## 问题

Bullet 的 compound 碰撞算法在调用任何窄相算法之前，先逐对检查子形状 AABB。凸形体的 AABB
紧贴几何（球的 margin 就是半径，盒的 margin 计入核心尺寸），所以这道检查的容差恰好是零：
只要两个子形状的 AABB 分开哪怕 1 µm，整对子形状就被跳过，既不会创建 manifold，也不会执行
窄相。外层 broadphase 与 `btCollisionDispatcher::getNewManifold` 接受的近接触范围更大，
与这道内层检查不一致，于是外层已经接受的近接触会被内层提前排除。

这个不一致在「轻轮 + 大静载」的载具上会被放大：轮子（球 + 柱 compound）与地形 section
（方块 compound）两侧都是 compound，任何正间隙都会丢掉支撑。台架实测（50 kg 轮、12.5 kN
预载、10 ms 子步、摩擦与恢复系数为 0）：裸球在 1 µm 间隙下仍有一个接触点、129.9 N·s 支撑
冲量，轮子几乎不动；把同一颗球包进 compound，同一间隙下接触点变成 0、冲量 0，轮子整个子步
自由落体，步末速度 −2.5981 m/s。

一次空档的代价是「这一步的支撑全部消失」，而不是此后连续自由落体：间隙重新落进窄相接受的
窗口，接触就恢复，只是那一步已经积了 2.5981 m/s。以 40 mm 起跳为例，第二个子步带着
189.71 N·s 冲量落地，步末速度从 −2.5981 回升到 −1.4019。

## 接触容差不是 2 cm

`gContactBreakingThreshold`（0.02）只是全局基准，成对阈值由
`btCollisionDispatcher::getNewManifold` 按形状缩放，`btCollisionShape::getContactBreakingThreshold`
即该缩放的实现：

```
getContactBreakingThreshold(default) = getAngularMotionDisc() * default
contactBreakingThreshold            = min(shape0->getContactBreakingThreshold(gContactBreakingThreshold),
                                          shape1->getContactBreakingThreshold(gContactBreakingThreshold))
disc(shape)                         = 形状包围球半径 + 球心偏移（btCollisionShape::getAngularMotionDisc）
```

`btCollisionDispatcher` 的构造函数里这段以 `CD_USE_RELATIVE_CONTACT_BREAKING_THRESHOLD` 为条件，
该 flag 属于 `m_dispatcherFlags` 的默认值，因此默认启用。

对这颗半径 0.84375 m 的轮胎球，AABB 是 2r 立方体，包围球半径取对角线的一半：

```
disc = 0.84375 × √3 = 1.461 m  →  阈值 = 1.461 × 0.02 = 29.2 mm
```

成对阈值取参与接触的两个**物体级**形状（整个 compound，而不是它的某个子形状）的 disc 最小值。
地形 section 的 compound 比这颗球大得多，`min` 取到球的 1.461，所以成对阈值就是 29.2 mm。
按固定 2 cm 加扩展仍会漏掉 20～29.2 mm 这一段：裸球在这一段有接触、能传 24.9 N·s 的支撑
冲量，compound 一个点都没有。**补丁因此按物体级形状计算成对阈值，而不是用全局常量。**

按子形状计算会把窗口压到 17.3 mm（`min(球的 1.461, 1 m 方块的 0.866) × 0.02`），20～29.2 mm
这一段照样丢。地表由许多小方块拼成时（真实地形就是），子形状与物体级形状的差别会实际出现，
台架用「256 个 1 m 方块拼成的地面」复现过。

## 补丁内容

[libbulletjme-compound-contact-tolerance.patch](libbulletjme-compound-contact-tolerance.patch)
改动两个文件、六处 culling 点：

| 文件 | 位置 | 用途 |
| --- | --- | --- |
| `btCompoundCollisionAlgorithm.cpp` | `ProcessChildShape` | 单个子形状对另一刚体的 AABB 检查 |
| 同上 | `processCollision` 的树裁剪 | 按 compound 局部空间整体裁剪子形状 |
| 同上 | `processCollision` 的清理循环 | AABB 分离即销毁子算法（连带释放 manifold） |
| `btCompoundCompoundCollisionAlgorithm.cpp` | `btCompoundCompoundLeafCallback::Process` | 子形状对子形状的 AABB 检查 |
| 同上 | `MycollideTT` 调用 | DBVT 树遍历阈值 |
| 同上 | `processCollision` 的清理循环 | 同上，成对销毁 |

共同写法是把额外扩展从 `m_resultOut->m_closestPointDistanceThreshold` 改成
`m_resultOut->m_closestPointDistanceThreshold + btPairContactTolerance(shapeA, shapeB)`，
`btPairContactTolerance` 就是上面那条成对阈值公式，与窄相接受的接触容差一致。

在单层 compound 布局下，六处传入的 `shapeA` / `shapeB` 都是**物体级**根形状：本侧是根
compound（`compoundShape`、`m_compoundColObjWrap->getCollisionShape()`），对方是根形状；
compound-compound 路径是两侧的根 compound。传子形状会把窗口压到 17.3 mm，见上一节。嵌套
compound 时 wrapper 的 `getCollisionShape()` 可能是子 compound，补丁不保证这一情形。

它不能改用 `m_resultOut->getPersistentManifold()->getContactBreakingThreshold()`：
`btCompoundCollisionAlgorithm` 自身不持有 manifold（`m_sharedManifold` 通常为空，manifold
由子算法各自持有）；`resultOut` 在第一个子形状处理前通常没有 manifold，处理过其他子形状后则
可能还挂着上一对的 manifold，因此不能保证拿到当前子形状对的有效阈值。退回全局常量 2 cm
同样会漏掉 20–29.2 mm。

`m_closestPointDistanceThreshold` 本身保持为 0：它大于 0 时两个 compound 算法都会切到
`BT_CLOSEST_POINT_ALGORITHMS` 并在每步结束后销毁子算法与 manifold，缓存生命周期随之改变。
补丁只加扩展量，不动这个开关。

## 构建

```bat
tools\native\build-libbulletjme-dpmt.bat <Libbulletjme 源码根> <输出目录>
```

脚本用 `VsDevCmd.bat` 启动一个带 MSVC 环境的 shell，再直驱 `javac -h` / `cl` / `link`。
官方指南里的 `./gradlew build` 走 Gradle 的 `cpp` 插件，该插件靠 `vswhere.exe` 或 VS 注册表
定位工具链；目标机器上两者都不存在，因此改走这条不依赖 Gradle 探测的路径。在工具链能被
Gradle 正常定位的机器上，`./gradlew -Ptarget=Windows64 -Pflavor=DpMt -Pbt=Release
bulletjmeWindows64ReleaseDpMtSharedLibrary` 与脚本等价。

`Libbulletjme` 工作树本身保持干净：构建前用
`git apply <本目录>/libbulletjme-compound-contact-tolerance.patch` 打上补丁，构建完
`git checkout -- <两个 .cpp>` 还原。

| 参数 | 值 | 理由 |
| --- | --- | --- |
| `/MT` | 静态 CRT | 出厂 DLL 只导入 `WS2_32`、`VCOMP140`、`KERNEL32`；`/MD` 会额外引入 `MSVCP140`/`VCRUNTIME140` |
| `/O2 /Ob3` | Release | 与 `build.gradle` 的 Release 分支一致 |
| `/openmp` | 多线程 flavor | `BT_THREADSAFE` + `BT_USE_OPENMP` 需要 |
| `/D BT_USE_DOUBLE_PRECISION` | 双精度 | Dp/DpMt flavor |
| `/D BT_THREADSAFE /D BT_USE_OPENMP` | 多线程 | DpMt flavor |
| `javac -h` | 生成 JNI 头 | `src/main/native/glue` 依赖 `com_jme3_bullet_*.h` |

## 安装

覆盖 `src/main/resources/natives/bullet_dpmt/windows/x86_64/bulletjme.dll`（SHA256 与字节数
见文首表格），然后重建 jar：

```bash
./gradlew --offline jar
```

**不要从上游发布包整包替换 `natives/`。** 十一个二进制里有十个是 23.1.0 出厂件、这一个是
23.1.2 加补丁的构建，整包覆盖会把补丁连同版本标记一起抹掉，而构建与测试都不会报错。替换前
比对文首表格的 SHA256。

覆盖前把出厂件另存一份（不要留在 `natives/` 里）；需要时从 Libbulletjme 的 23.1.0 发布包取回。

运行中的 JVM 会打印 `Dp_Mt_Libbulletjme version 23.1.2 initializing`；版本号来自源码树，
可以与出厂 DLL 的 `23.1.0` 区分。

## 退场条件

补丁是上游修正到位之前的临时构件。上游发布了包含同等修正的构件之后：

1. 用该构件替换 `natives/bullet_dpmt/windows/x86_64/bulletjme.dll`，其余 flavor 一并升到同一版本；
2. 删除本目录的补丁、脚本与本文；
3. 更新文首 SHA256 到新构件。

在此之前不要只删补丁：没有补丁的构建在这个引擎问题上与上游出厂件等价。

## 消费方必须配合的部分

原生只保证「近接触不会被提前排除」。游戏层若在回调里按高度场判据否决这些点，原生补丁的
效果会被整个抵消：球面与地面相差 1 µm 时，高度场读数就已经是「未侵入」，而支撑力正是靠这段
近接触间隙传递的。实测（同一颗球、12.5 kN 预载、10 ms 子步）：

| 1 微米间隙 | 第一步支撑冲量 | 第一步末向下速度 | 下一步冲量 |
| --- | ---: | ---: | ---: |
| 原生补丁直接求解 | 129.9 N·s | 0.0001 m/s | 129.9 N·s |
| 加上「高度场未侵入即拒绝」 | **0** | **2.5981 m/s** | **285.79 N·s** |
| 加上「未侵入即禁用（distance1=5000）」 | **0** | **2.5981 m/s** | **285.79 N·s** |

承载面（法线竖直分量 ≥ 0.7）一律保留，只有台阶立面、墙这类竖直面允许被丢弃；上表后两行丢掉的正是
承载面。

### 分离点会带着棱上的法线进来

补丁把子形状的窗口放开到成对阈值之后，进入窄相的不只是贴着的点，还有一批 `distance1 > 0` 的未接触
点。它们的法线来自窄相找到的那一对最近特征：地形由盒拼成，球压过邻块棱线时，最近点落在盒子的**棱**
上而不是面内，「棱→球心」就是法线方向，于是平地接缝上也会读到一条几乎竖直、却带 0.38 横向分量的
法线（`|n.y| ≈ 0.92`）。两侧柱顶等高时这是幻觉——真实的最近特征在盒面内部，法线本该竖直。Bullet
自带 `btInternalEdgeUtility` 处理三角网上的同一件事（法线换成面法线、接触点挪进面内），盒拼出来的
地形没有对应工具。

求解器不跳过这些点，支撑力正是靠它们穿过间隙传出去：速度约束对正距离点扣掉的容许接近量是
`distance1 / dt`（8 mm 间隙、10 ms 子步约 0.8 m/s），更快的接近速度照样被抵消。同一条性质也让歪
法线有了后果——约束算的是**接触点**的速度 `v + ω × r`，作用点落在棱线上、离形心 5–12 cm 时，
`ω × r` 有沿法线的分量：自转 176 rad/s 时是 9–21 m/s。求解器把这个并不存在的接近速度当真，沿法线
给冲量，轮子被弹起来。

处置分两种，取决于对方刚体会不会自转：

| 对方刚体 | 处置 | 实测（最坏相位） |
| --- | --- | --- |
| 锁转（ω = 0） | 法线改竖直，距离与接触点不动 | 6 m/s 抬升 0.0426 → 0.0024 m；24 m/s 抬升 0.5622 → 0.0000 m |
| 自转 176 rad/s | 法线改竖直，分离点的作用点挪到形心正下方 | 单子步竖直速度 +28.2 → +0.155 m/s；实机突增 36 → 0 |

自转那一行的实机读数取自中位车速 58.8 m/s、598 次跨缝的一轮：单子步竖直速度的突增（`Δv_y > 6 m/s`）
36 次 → 0 次，峰值 +15.96 → +0.097 m/s。

锁转体的 `ω × r` 恒为零，力臂进不了求解器，换法线就够。有自转时 `ω × r` 的竖直分量就是被弹起来的
那一份，作用点必须一起挪到形心——两侧等高时这等效于把那张平面当成无限平面取接触点，法向约束与承
载都保留。

消费方要记住两条：

1. **朝上又带着正距离的法线先当可疑。** 实测的斜法线在 `|n.y| ≈ 0.92`，不要拿它算坡度、攀爬面或
   承载判据；方块地形的真实面接触，法线贴着世界轴。
2. **不要整条丢掉。** 丢掉等于把那段间隙的支撑一起丢掉（上面的实测表后两行：那颗球第一步即 0 冲量、
   2.5981 m/s 向下速度；接缝处实测沉降 26 mm）。保留距离、只换法线是安全的下限；只有对方会自转时，
   才需要同时挪作用点。

只在两侧等高时归轴：台阶与悬崖两侧不同高，那里的斜法线是真实几何，归轴会把它抹掉，攀爬与落地都要
靠它。

这些点是窄相与 dispatcher 阈值的产物，补丁只决定它们能不能走到窄相：上游修好 culling 之后，盒拼
的地形照样会在 29.2 mm 以内报出正距离点。因此这一节属于长期约束，不随补丁退场。

## 验证

下表是成对阈值两侧的读数（接触点数与第一步支撑冲量），第一列是初始间隙：

| 初始间隙 | 裸形状 | 任一侧 compound（固定 2 cm 扩展） | 任一侧 compound（按形状计算） |
| ---: | --- | --- | --- |
| −1 µm | 1 点，129.9 | 1 点，129.9 | 1 点，129.9 |
| 0 | 1 点，129.9 | 1 点，129.9 | 1 点，129.9 |
| +1 µm | 1 点，129.9 | 1 点，129.9 | 1 点，129.9 |
| +10 mm | 1 点，79.9 | 1 点，79.9 | 1 点，79.9 |
| +20 mm | 1 点，29.9 | **0 点，无 manifold** | 1 点，29.9 |
| +21 mm | 1 点，24.9 | **0 点** | 1 点，24.9 |
| +25 mm | 1 点，4.9 | **0 点** | 1 点，4.9 |
| +29 mm | 1 点，0.0（有接触无载荷） | **0 点** | 1 点，0.0 |
| +35 mm | 0 点 | 0 点 | 0 点 |
| +40 mm | 0 点 | 0 点 | 0 点 |

四种 compound/裸形状组合（轮子侧、地形侧、两侧）在整段区间内逐点一致，边界也一致。

[NearContactRepro.java](NearContactRepro.java) 是产生上表的独立程序，只依赖 Libbulletjme，
不依赖模组、Gradle 或测试框架（需要 JDK 17+，代码用了 record）。它把「裸球 / compound 球」与
「单盒地面 / 256 个小盒拼成的地面」两两组合各扫一遍，`-Dmm.native=<path>` 指定要测的构件：

```bash
javac -cp Libbulletjme.jar NearContactRepro.java
java -cp "Libbulletjme.jar;." -Dmm.native=C:\path\to\bulletjme.dll NearContactRepro   # Windows
java -cp "Libbulletjme.jar:." -Dmm.native=/path/to/libbulletjme.so  NearContactRepro   # Linux, macOS
```

出厂构件与打过本补丁的构件各跑一次，就能看到 `+1 µm` 那一行从「0 点、−2.5981 m/s」变成
「1 点、129.9 N·s、−0.0001 m/s」。两份构件都如实报出自己那一组读数，所以这同时是「当前加载的是
哪一份二进制」的判据。

## 上游

[upstream-issue.md](upstream-issue.md) 是给 Libbulletjme 的 issue 草稿，含最小复现与数值表；
补丁正文以折叠块嵌在「建议修法」一节，整篇可以直接贴进 issue，不需要外链。
草稿里的建议修法与本地补丁等价。
