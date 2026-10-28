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

原 DLL 备份在 `Machine-Max/build/native/orig-bulletjme.dll`（`build/` 不入库；需要时从
Libbulletjme 的 23.1.0 发布包取回）。

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

下游（Machine-Max）的 `CollisionHandler#isSupportingTerrainContact` 把承载面（法线竖直分量
≥ 0.7）与台阶立面分开，只有立面允许被丢弃，承载面一律保留。判据与实测数据见该仓库
`docs/plan/进行中/碰撞系统-撞碎与压溃实现计划.md` 的 §2.3。

## 验证

`Machine-Max/src/test/java/io/github/sweetzonzi/machine_max/mech/collision/CompoundContactToleranceTest.java`
比较同一间隙下 compound 与裸形状的接触点数、距离与冲量，覆盖到成对阈值两侧：

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

该测试读的是运行期加载的 DLL，因此对未打补丁的 flavor 会如实失败——它同时是「当前 JVM 用的
是不是打过补丁的二进制」的探针。设置环境变量 `MM_NATIVE_PATH=<某个 native 路径>` 可以让它
加载任意一份构件，用于出厂件与重建件的对比。

[NearContactRepro.java](NearContactRepro.java) 是同一套台架的独立版本，只依赖 Libbulletjme，
不依赖模组、Gradle 或测试框架（需要 JDK 17+，代码用了 record）。它把「裸球 / compound 球」与
「单盒地面 / 256 个小盒拼成的地面」两两组合各扫一遍，`-Dmm.native=<path>` 指定要测的构件：

```bash
javac -cp Libbulletjme.jar NearContactRepro.java
java -cp "Libbulletjme.jar;." -Dmm.native=C:\path\to\bulletjme.dll NearContactRepro   # Windows
java -cp "Libbulletjme.jar:." -Dmm.native=/path/to/libbulletjme.so  NearContactRepro   # Linux, macOS
```

出厂构件与打过本补丁的构件各跑一次，就能看到 `+1 µm` 那一行从「0 点、−2.5981 m/s」变成
「1 点、129.9 N·s、−0.0001 m/s」。

## 上游

[upstream-issue.md](upstream-issue.md) 是给 Libbulletjme 的 issue 草稿，含最小复现与数值表；
补丁正文以折叠块嵌在「建议修法」一节，整篇可以直接贴进 issue，不需要外链。
草稿里的建议修法与本地补丁等价。
