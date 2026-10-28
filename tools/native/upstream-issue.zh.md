<!-- 标题：将球体包进 CompoundCollisionShape 会丢失近接触并改变支撑冲量 -->
<!-- 本文件的注释行不会渲染；标题取上一行，其余全部是 issue 正文。 -->

> 说明：这是 [upstream-issue.md](upstream-issue.md) 更新后的中文翻译稿。Java 复现代码与
> 补丁 diff 未翻译，保持英文原样；需要核对代码时请看英文稿。

## 摘要

两个除此之外完全相同的场景：几何相同、初始位置相同、受力相同。唯一区别是，一个场景里的
球体被包进 `CompoundCollisionShape`，另一个场景直接使用球体。

对包装后的球体，在 dispatcher 接触阈值（这颗球是 29.2 mm）以内的**每一个测试过的正间隙**
上，接触点都会消失。间隙为 1 µm 时，一个本应承载 129.90 N·s 支撑的步承载 0，刚体以完全
不受支撑的速度结束该步。

## 预期行为

把形状包进 compound 不应改变它与同一几何在未包装时产生的接触，也不应改变这些接触承载的
支撑。1 µm 的间隙就应表现得像 1 µm 的间隙，无论其中一侧是不是 compound。

## 在出厂构建上的实测

出厂 Libbulletjme 23.1.0 release 二进制，无本地补丁。

场景：轮子是 `SphereCollisionShape(r = 0.84375)`，质量 50 kg，每步施加 12500 N 向下中心力，
另加重力 9.81 m/s²，固定步长 10 ms，摩擦 0，恢复系数 0，无阻尼，无休眠。
地面是静态 `BoxCollisionShape(8, 0.5, 8)`，顶面在 y = 0。没有支撑时每步获得的速度是
2.5981 m/s；完全支撑的步结束时为 0。

| 间隙 | 裸球 | 同一球包进 `CompoundCollisionShape` |
| ---: | --- | --- |
| -1 µm | 1 个接触，129.91 N·s，0.0000 m/s | 1 个接触，129.91 N·s，0.0000 m/s |
| 0 | 1 个接触，129.90 N·s，0.0000 m/s | 1 个接触，129.90 N·s，0.0000 m/s |
| +1 µm | 1 个接触，129.90 N·s，-0.0001 m/s | **0 个接触，0 N·s，-2.5981 m/s** |
| +1 mm | 1 个接触，124.91 N·s，-0.1000 m/s | **0 个接触，0 N·s，-2.5981 m/s** |
| +5 mm | 1 个接触，104.91 N·s，-0.5000 m/s | **0 个接触，0 N·s，-2.5981 m/s** |
| +10 mm | 1 个接触，79.91 N·s，-1.0000 m/s | **0 个接触，0 N·s，-2.5981 m/s** |
| +15 mm | 1 个接触，54.91 N·s，-1.5000 m/s | **0 个接触，0 N·s，-2.5981 m/s** |
| +20 mm | 1 个接触，29.91 N·s，-2.0000 m/s | **0 个接触，0 N·s，-2.5981 m/s** |
| +21 mm | 1 个接触，24.90 N·s，-2.1000 m/s | **0 个接触，0 N·s，-2.5981 m/s** |
| +25 mm | 1 个接触，4.91 N·s，-2.5000 m/s | **0 个接触，0 N·s，-2.5981 m/s** |
| +29 mm | 1 个接触，0.00 N·s，-2.5981 m/s | 0 个接触，0 N·s，-2.5981 m/s |
| +35 mm | 0 个接触，-2.5981 m/s | 0 个接触，-2.5981 m/s |

+20 mm 这一行是关键：未包装的球体仍然得到接触点和 29.91 N·s 支撑，说明窄相确实会在
29.2 mm 阈值之内报告近接触。（求解器把这些当作预测性接触：`penetration = distance +
linearSlop`，`velocityError -= penetration * invTimeStep`，因此这一步可以闭合间隙，但不会
继续穿透。）包装后的球体什么都得不到。

compound 可以在任意一侧。把地面表示为 256 个 1 m 方块组成的 compound，平铺同样的
16 × 1 × 16 m 板，球体保持裸球：

| 间隙 | 裸球，compound 地面 |
| ---: | --- |
| -1 µm | 4 个接触，121.54 N·s，-0.1723 m/s |
| +1 µm | **0 个接触，0 N·s，-2.5981 m/s** |
| +20 mm | **0 个接触，0 N·s，-2.5981 m/s** |

（四个点而不是一个点是预期的：四个子方块接触球体。表中要说明的是 +1 µm 处的转变。）

从表面上方 40 mm 开始，跑两步：

| 配置 | 第 1 步 | 第 2 步 |
| --- | --- | --- |
| 裸球，box 地面 | 0 个接触，-2.5981 m/s | 1 个接触，189.71 N·s，-1.4019 m/s |
| compound 球，box 地面 | 0 个接触，-2.5981 m/s | **0 个接触，-5.1962 m/s** |

## 两个阈值从哪来

`btCollisionDispatcher::getNewManifold()` 为每对物体创建 manifold 时使用：

```
contactBreakingThreshold = min(shapeA->getContactBreakingThreshold(gContactBreakingThreshold),
                               shapeB->getContactBreakingThreshold(gContactBreakingThreshold))
```

其中 `btCollisionShape::getContactBreakingThreshold(default)` 返回
`getAngularMotionDisc() * default`（由 `CD_USE_RELATIVE_CONTACT_BREAKING_THRESHOLD` 控制，
默认开启）。对半径 0.84375 m 的球，即 `0.84375 * sqrt(3) * 0.02 = 29.2 mm`。

两个 compound 算法却只用 `btManifoldResult::m_closestPointDistanceThreshold` 填充子形状对的
AABB 检查；该值在 closest-point 模式关闭时为 0。于是内层检查要求实际重叠，而 manifold 能
容忍最多 29.2 mm 的分离，中间这一整段在任何窄相算法运行之前就被丢掉了。

涉及的位置是 `btCompoundLeafCallback::ProcessChildShape`、`btCompoundCollisionAlgorithm::
processCollision` 里的树裁剪和子算法清理循环，以及 `btCompoundCompoundLeafCallback::Process`、
`MycollideTT` 调用和 `btCompoundCompoundCollisionAlgorithm::processCollision` 里的成对清理循环。

`m_resultOut->getPersistentManifold()->getContactBreakingThreshold()` 在这里**不能依赖**。
compound 算法本身不持有 manifold（`m_sharedManifold` 通常为空；manifold 属于各个子算法）。
`resultOut` 在第一个子形状处理之前可能没有 manifold；处理过兄弟子形状之后，它又可能还挂着
那个子形状的 manifold。因此不能保证拿到属于当前待测子形状对的有效 manifold，尽管这个指针
有时确实非空。

## 复现器

单文件，只依赖 Libbulletjme，需要 JDK 17 或更高（用到了 record）。`-Dmm.native` 选择要测试的
native 库，因此可以在不移动文件的情况下对比出厂构建和本地重建构建。

```bash
javac -cp Libbulletjme.jar NearContactRepro.java
java -cp "Libbulletjme.jar;." -Dmm.native=C:\path\to\bulletjme.dll NearContactRepro   # Windows
java -cp "Libbulletjme.jar:." -Dmm.native=/path/to/libbulletjme.so  NearContactRepro   # Linux, macOS
```

完整 Java 源码见 [upstream-issue.md](upstream-issue.md) 中的 `NearContactRepro.java` 折叠块。

## 建议修法

从两个物体级形状复现 dispatcher 的公式，并在全部六个剔除点使用：

```cpp
static btScalar btPairContactTolerance(const btCollisionShape* shapeA, const btCollisionShape* shapeB)
{
    return btMin(shapeA->getContactBreakingThreshold(gContactBreakingThreshold),
                 shapeB->getContactBreakingThreshold(gContactBreakingThreshold));
}
```

然后把每个 `btVector3 extend(m_resultOut->m_closestPointDistanceThreshold, ...)` 换成
`m_closestPointDistanceThreshold + btPairContactTolerance(a, b)`。

要让 dispatcher 与 compound 层一致，两个形状都应是**物体级**形状：两侧的根 compound，而不是
子形状。传子形状会把窗口压窄——对 1 m 方块子形状，这一项会变成
`min(1.461, 0.866) * 0.02 = 17.3 mm`，20–29 mm 这一段仍然会断。`btCompoundCompoundLeafCallback`
提供 `m_compound0ColObjWrap` / `m_compound1ColObjWrap`；`ProcessChildShape` 有
`m_compoundColObjWrap`。注意：下面的补丁传的是 wrapper 的 `getCollisionShape()`，对本文实测的
单层布局它就是根形状。嵌套 compound 时，这个形状可能是子 compound，因此补丁不保证嵌套情况下
的物体级阈值；见“补丁做到与没做到什么”。

`m_closestPointDistanceThreshold` 本身保持为 0：把它调大后，两个 compound 算法都会切换到
`BT_CLOSEST_POINT_ALGORITHMS`，并在每步销毁子算法和 manifold，这是另一项改动。

针对 `78b76008` 的补丁 diff 见 [upstream-issue.md](upstream-issue.md) 中的折叠块。

## 补丁做到与没做到什么

- 它恢复了测试过的正间隙上的近接触：地面为 256 个方块时，从 +1 µm 到 +25 mm 都能重新出现
  接触，而出厂构建在这些间隙上完全没有接触。在**同一种地面表示**下，包装球和裸球现在一致。
  不同的地形分解方式仍可能产生不同的冲量；补丁并不声称这一点也一致。
- 它**不是**对 dispatcher 阈值在所有布局下的完整复现。这些扫描里的轮子是只有一个居中子形状的
  compound；地面 compound 有 256 个子形状。偏移的轮子子形状、嵌套 compound 和其他布局都没有
  验证。
- 关闭 `CD_USE_RELATIVE_CONTACT_BREAKING_THRESHOLD` 时，dispatcher 使用裸露的
  `gContactBreakingThreshold`（0.02）。这个 helper 仍然按 disc 缩放，对 disc 小于 1 的形状会
  覆盖不足；它不是单纯地扩得太大。
- 成本没有做基准测试。额外扩展量以两个 disc 中较小的一个为界，但两个大 compound 仍可能达到
  几十厘米，并让更多子算法保持存活。

## 开放问题

1. **成本。** 额外的扩展量可以接受吗，还是应该加一个上限（例如限制到这一对自己的 AABB
   对角线）？我没有能单独隔离这一点的基准测试。
2. **范围。** 这个不匹配是对称的，因此两个 compound 算法都需要改；只修一侧会让 `MycollideTT`
   继续丢掉叶子回调本会保留的 pair。
3. **上游 Bullet。** 如果零填充是有意为之，那么把这一区间写进文档、并在应用层提供更宽容差的
   请求也可以。无论哪种方式，dispatcher 和 compound 层都应该一致。

## 环境

- 在出厂 Libbulletjme 23.1.0 release 二进制上复现（DpMt，Windows x64）。
- 相关 native 文件——两个 compound 算法、`btCollisionDispatcher`、`btManifoldResult`、
  `btCollisionShape`、`btPersistentManifold`——在 23.1.0、23.1.2、23.2.0 和当前 `master` 之间
  没有变化（`git diff` 为空）。这让同样的行为很可能仍然存在，但不能替代在 23.2.0 上的实际运行；
  上面的 23.1.0 数据才是运行期证据。
- 补丁从提交 `78b76008` 构建，该库自报 23.1.2，所以两者是不同的提交，而不是同一个构建的
  before/after。
- 上面的数字来自本报告中的复现器，以及一个跑同样扫描的 JUnit 测试。

## 当前规避方案

我们在本地从源码构建 Libbulletjme 并打上补丁。在我们的载具上，症状是轮子只要与地形分离，
就会丢掉整整一个 10 ms 子步的支撑，于是应用看到“无接触的一步”后面跟着“冲量异常大的一步”。
在应用层把这些过滤掉只能压住症状，不能恢复支撑，所以我报告的是引擎侧行为，而不是一个规避
方案。
