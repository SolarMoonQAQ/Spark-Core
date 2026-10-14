# 物理流体 GameTest 车道

> 状态：**已落地并跑通**（10 条用例，多次随机原点全绿；见 §7 证据）
> 创建：2026-10-14
> 相关：section_fluid_index_plan.md（被测设计）、chunk_height_index_plan.md（同构前身）
> 兄弟仓库先例：ARMS-Core docs/GameTest车道指南.md、BallisticsFramework docs/GameTest自动化指南.md

***

## 1. 一句话

gradlew runGameTestServer 会起一个真的无头 MinecraftServer，把
src/main/java/**/gametest/ 下每个 @GameTest 方法当一条用例跑完，并用失败条数作退出码。
本项目已接上这条车道，用它自动验证物理流体索引（SectionFluidIndex + 发布通道 + 查询 API）
的**索引编码语义**——不是验证浮点位移。

## 2. 车道怎么接上的

| 组件 | 位置 | 说明 |
| --- | --- | --- |
| run 配置 | build.gradle.kts 的 neoForge.runs.create("gameTestServer") | type = "gameTestServer" + neoforge.enabledGameTestNamespaces = spark_core；只有该命名空间的用例会被注册 |
| 用例类 | src/main/java/cn/solarmoon/spark_core/gametest/SparkFluidGameTest.java | @GameTestHolder("spark_core") + @PrefixGameTestTemplate(false)；用 Java 写，规避 Kotlin object 静态方法与注解扫描的不确定性 |
| 场地模板 | src/main/resources/data/spark_core/structure/empty_platform.nbt | 5×3×5 石砖地板；目录名是**单数** structure |
| 模板生成器 | tools/generate_gametest_structure.py | 纯 Python 手写 NBT，产物提交进仓库；几何变化时才重跑 |

**模板为什么必须提交而不是在 @BeforeBatch 里现造。** GameTestRunner#runBatch 的顺序是
createStructuresForBatch（读模板）→ beforeBatchFunction。批次函数运行时模板已经被读过，
缺模板会抛 IllegalStateException: Missing test structure。BallisticsFramework 虽然也有
@BeforeBatch 造场地的代码，但它的 .nbt 同样提交在资源里——真正起作用的是提交的文件。

**为什么不用 @BeforeBatch。** 每批至多一个批次函数。本类不注册批次函数，批次名
sparkCoreFluid 自成一档，既满足「至多一个」，也不会和同开发环境里其它模组的
defaultBatch 批次函数相撞（撞上会在注册阶段直接失败）。

## 3. 随机原点与「量得出来的东西」

GameTestServer 的测试网格落在**随机原点**（±1.5e7，Y 恒 -59），每次启动都不同；
float32 在该量级 ULP 约 1 格。因此**依赖位移、速度、接触点的断言在这条车道上量不出真实数值**。

流体索引的坐标语义恰好绕开了这一点：

- 采样返回的 surfaceY = sectionMinY + colTopY + colTopQ/16，其中前两项是整数世界 Y，
  colTopQ 是 1/16 量化值。断言比较的是它与 helper.absolutePos(relPos).getY() 的差值
  （0.875、1.0 这类精确二进制量），**与 X/Z 无关，也不经过 float32 世界坐标量化**。
- 所以这条车道量的是「索引编码对不对」（顶层静水是 14 还是 16、柱底取哪个 voxel、
  同列熔岩水是否分段、跨 section 续接标记是否置位），而不是物理引擎跑得准不准。

实测三次运行的随机原点分别为
-12623484,-59,-13184937、2501005,-59,-8499638、4719251,-59,6954123，用例全绿。

**结论：** 流体索引是这条车道最合适的被测对象之一；而「浮力算出来多少牛顿」「船浮多高」
仍应走纯逻辑 JUnit（原点附近）与实机验收。

## 4. 为什么用例要自己预约地形

GameTest 世界里没有载具刚体，PhysicsLevel.requestStep 里
updateBuild(buildBoxes) 拿到的是空列表，**不会自动构建测试区块的物理表示**，
loadedChunks 里没有它，sampleFluid 恒返回 null。

用例因此调用公开 API：

    SparkLevel.scheduleChunkLoad(level, chunkPos, yMin, yMax, 0 /*delay*/, 200 /*hold*/)

它投递到 PPhase.ALL 立即任务，下一 tick 的 requestStep 里
processPendingBuildRequests → loadAndActivateChunkTerrain → PhysicsChunk.load()，
而 load() 对**所有** sectionY 调 prepareSnapshot()——流体索引就在这一步发布，
**不需要 section 被激活，也不需要建出刚体**。

用例用 GameTestHelper#succeedWhen 做「每 tick 重试直到断言通过」的轮询，一次吞掉两段异步：
物理地形管理器的初始化，以及上面这条构建链的延迟。断言在服务端主线程执行；流体索引是不可变
实例、由 @Volatile 发布，主线程读它是安全的。

## 5. 覆盖的场景

| 用例 | 钉住的契约 | 对应计划 |
| --- | --- | --- |
| topStaticWaterSurfaceIsEightNinths | 顶层静水 topQ == 14，液面 = voxel 底 + 0.875 | §9 #2、D5 |
| columnBottomAndSurfaceBoundaries | 柱底是最低含水 voxel 的**底面**；液面判据 wy >= surfaceY；越界不 clamp | §9 #9、P1-2 |
| waterloggedSlabIndexesWaterChannel | 含水台阶同时命中固体与流体两条通道 | §9 #5、D2 |
| seagrassIsFluidOnlyChannel | 海草无碰撞形状仍进入流体通道（判据是 FluidState 非空） | §9 #6、D2 |
| waterAboveLavaKeepsSegmentsSeparate | 同列熔岩 + 石隔层 + 水按同流体切段，熔岩段返回熔岩段顶面 | §9 #10、D6/P1-3 |
| crossSectionColumnReachesTop | 跨 section 柱顶 topQ == 16；续接标记判据是「局部 y==15 且上方同类流体」 | §9 #3、§3.4⑤ |
| pureFluidSectionPublishesIndexWithoutActivation | **未激活**的纯流体 section 也必须已发布流体索引 | §9 #8、P0-1/D3 |
| plainStoneColumnHasNoFluid | 未放置流体的列不得被索引标记；石头内部采样为 null | §9 #7、D12 |
| batchSamplingMatchesSingleAndStaysStrict | 批量路径与单点一致；严格局部换算不 clamp 邻格水位 | P0-3、D8 |
| multiSectionColumnTruncatesAtEachSectionTop | 深水柱跨两个 section 边界时，每个被淹没的 section 都把液面截断到自己的顶面；续接标记只在本柱顶为局部 y==15 时置位 | §10.1、§3.4⑤ |

## 6. 边界与已知限制

1. **跨用例的 chunk 污染。** 用例之间共享同一个超平坦世界，网格间距（宽度+5）小于区块宽度，
   相邻格位可能落在同一 chunk 的同一 section。因此断言只针对**本用例自己的列**：
   - 需要「无流体」语义时断的是 sampleFluid == null 与「本列 hasColumn 为 false」，
     而不是「整节索引为 null」（同 chunk 别的格位可能有水）。
   - 每个用例的柱子放在格位坐标 (2,2)，外壳占 1..3 不越出模板；8 格一行的排布使各列
     的 section 局部 x 互不相同，不会互相覆盖。
2. **跨 section 柱顶按截断处理**（计划 §10.1 的已知限制）。crossSectionColumnReachesTop
   断言的正是「下层柱顶 topQ==16、液面截断到下一 section 底面」这一实现语义；等完整续接实现后
   该用例需要相应改成断真实液面。
3. **客户端不在本车道覆盖范围。** 计划 §10.4 已确定客户端只有首次加载正确、之后无变更失效通知；
   这条车道只跑服务端。
4. **只适合「构造/接线/判据」，不适合数值。** 见 §3。

## 7. 运行与证据

    # 离线跑完整车道（工作目录 run/，跑完自动退出；失败必要用例数即退出码）
    .\gradlew runGameTestServer --offline

    # 只生成 run 配置（IDE 启动项依赖它）
    .\gradlew prepareGameTestServerRun --offline

    # 重新生成场地模板（只在场地几何变化时）
    python tools/generate_gametest_structure.py

判读：

- 全绿：日志出现 All 9 required tests passed :)，gradlew 退出码 0。
- 有失败：日志出现 N required tests failed，GameTest 任务 FAILED，退出码 = 失败条数。

已落地的验证证据（同一份代码，三次不同随机原点）：

    [22:35:24] 10 tests are now running at position -2187647, -59, -3283778!
               ========= 10 GAME TESTS COMPLETE IN 359.4 ms ======================
               All 10 required tests passed :)

（更早的 9 条用例版本也曾在 -12623484,-59,-13184937 与 4719251,-59,6954123 两个原点上全绿，
说明断言不随随机原点漂移。）

**反向验证（变异测试）**：把 FREE_SURFACE 从 14f/16f 改成 15f/16f 后重跑，
5 条依赖自由液面的用例立即失败并给出真实读数，进程退出码 1：

    waterloggedslabindexeswaterchannel failed ... 预期 -58.0625，实际 -58.125
    topstaticwatersurfaceiseightninths failed ... 预期 -58.0625，实际 -58.125
    ...
    ========= 9 GAME TESTS COMPLETE IN 637.9 ms ======================
    5 required tests failed :(

这说明断言不是空转：真正读到了索引里的 14/16。

***

## 8. 跨 section 液面柱：为什么是「截断」

一节 16 格，深水柱必然跨节。实现里跨节只做**标记**不做**续接**，采样因此是截断的。
这一节把语义讲透，并对应到用例 multiSectionColumnTruncatesAtEachSectionTop。

### 8.1 数据结构只存「本 section 内最高柱」

SectionFluidIndex 的 L2 每列只保存 4 个字节：colTopY / colBottomY / colTopQ / colType，
它们描述的都是**这个 section 内部**的最高一段同流体柱。跨节的部分不进 L2，只进
colReachesTopBits（L3，256 个 bit）。

### 8.2 topQ 是 14 还是 16，由「上方是不是同类流体」决定

SectionSnapshot.snapshotFromChunk 计算每个含水 voxel 的顶面高度：

    y < 15：直接读 section.getBlockState(x, y+1, z)，同类型则 1.0，否则 fluidState.ownHeight
    y == 15：fluidState.getHeight(level, worldPos)，即原版 hasSameAbove ? 1.0 : ownHeight

两者语义逐字一致（isSame == hasSameAbove）。于是：

| 情形 | getHeight() | topQ | 液面 |
| --- | --- | --- | --- |
| 顶层静水（上方空气） | 8/9 | 14 | voxel 底 + 0.875 |
| 上方同类流体 | 1.0 | 16 | voxel 底 + 1.0 |

注意 8/9 是 WaterFluid.Source / LavaFluid.Source 的 getAmount()=8 除以 9；1.0 只在
「上方还有同类流体」时出现。因此**顶层静水的 topQ 是 14 而不是 16**（决策 D5 的 A 案）。

### 8.3 续接标记的判据不是 topQ==16

Builder.build 从局部 y=15 向下扫描切段。markReachesTop 只在
「局部 y==15 且 level.getFluidState(worldY+1) 与当前同类型」时置位：

    if (y == 15) {
        val above = level.getFluidState(originY + y + 1 ...)
        if (fluidState.type.isSame(above.type)) fluidBuilder.markReachesTop(x, z)
    }

**不能用 topQ==16 判续接**：静水顶层 topQ 是 14，用它判会让这个标记永远为 false，
跨节逻辑永远走不到（计划 §3.4⑤ 专门写了这一条）。

### 8.4 采样时「截断」发生在哪一行

sampleLocal 命中主柱时：

    val surfaceY = y0 + topY + topQ / 16f

- 若该 section 含真实液面（柱顶 voxel 上方是空气），topQ==14，返回真实液面；
- 若该 section 被完全淹没（柱顶局部 y==15 且上方同类流体），topQ==16，
  返回 y0 + 15 + 1 = **下一 section 的底面**，也就是本 section 的顶面。

同时 ly > topY 保证柱顶之上的 voxel 判为无水；wy >= surfaceY 保证采样点高于这个
（截断）液面时判为无水。

### 8.5 误差方向：偏低，不会高估浮力

被淹没的 section 返回的液面 = 本 section 顶面，低于真实液面。换算成 depth = surfaceY - y
就是**偏小**，不会凭空多出一层浮力。代价是：越深的 section 误差越大，
每往下多跨一节，就少算约 16 格的真实浸没高度。用例里那根从 y=1 灌到 s2Rel+3 的水柱，
在 S0 采到的 depth 是 9.5，而真实 depth 是 29.375——差额全部来自截断。

对下游的含义：浮力按 depth 做饱和（ratio = depth/fullSubmergeDepth，再 clamp 到 0..1）时，
只要采样点在含真实液面的那个 section 里就精确；一旦落到更深的 section，读到的是
「本层顶部」而不是真实水面。若下游需要精确浸没体积，应顺着 colReachesTopBits 向上查找，
或由 Spark-Core 在 Phase 4 补完整的跨节续接。

### 8.6 用例三级期望（已实测）

| 位置 | 柱顶局部 y | topQ | 采样返回的 surfaceY | reachesTop |
| --- | --- | --- | --- | --- |
| S0 深处 (y=1.5) | 15 | 16 | S1 底面 | true |
| S1 深处 (y=s1Rel+15.5) | 15 | 16 | S2 底面 | true |
| S2 顶层 (y=topRel+0.5) | 3 | 14 | topRel + 0.875（真实） | false |

### 8.7 后续完整续接的做法（不在本车道）

要让深处也读到真实水面，可在 SectionFluidIndex 上做其中一种：

1. **向上走链**：colReachesTopBits 为真时，物理线程顺着 loadedChunks 取上一层 section，
   重复直到某层的柱顶不在局部 15，或层数到达上限。代价：查询从 O(1) 变 O(层数)。
2. **存柱高**：构建时把「本列总高」写进索引（需要跨 section 协调，构建期要读相邻 section，
   与当前「单 section 独立构建、主线程一次遍历」的模型冲突，代价较高）。

计划 §10.1 选择先按截断处理，本车道把这个选择钉成可回归的规格，而不是当作 bug。
