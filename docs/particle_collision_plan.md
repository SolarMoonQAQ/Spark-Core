# Spark-Core 粒子地形交互（方块碰撞）实施计划

> **状态**: P0–P2 已实现（客户端验证与基准未做，见 §11 实施进度；§1、§2 记录的是计划创建时的缺口） · **创建**: 2026-09-30 · **修订**: 2026-10-02\
> **目标版本**: Spark-Core 1.x\
> **上游文档**: [particle_soa_plan.md](./particle_soa_plan.md)（本文档补全其 Phase 4「粒子碰撞（方块碰撞）」条目；对应阻塞项 **B12** 已同步补入上游文档，见附录 C「上游文档同步」）\
> **核对基线**: Spark-Core `13c40cd`（2026-09-30）· SimpleBedrockModel `1.21.1-dev@c314b1e`（`2.4.12-neoforge-mc1.21.1-3-gc314b1e`，LGPL-3.0）· NeoForge `21.1.219` 反编译源（`build/moddev/artifacts/neoforge-21.1.219-minecraft-sources.jar`）\
> **参考源**: 语义基线为 Bedrock `particle_effect` v1.10.0 规范（下文以 Microsoft Learn 组件文档为准：`minecraft:emitter_local_space`、`minecraft:particle_motion_collision`、`minecraft:particle_motion_parametric`，最后更新 2023-09-20）+ 原版 `Particle.move` / `Entity.collideBoundingBox`；SimpleBedrockModel（下称 SBM）只作**行为对照**。第 3 节引用 SBM 行号仅为对照说明，**不得带入实现代码与注释**（许可边界见 §8）。SBM 源码当前不在本工作区，其行号与公式无法就地复核（见 §8）\
> **范围排除**: 非世界空间的粒子**不参与碰撞**——局部空间粒子（`emitter_local_space.position=true`）与脚本粒子（`particle_motion_parametric`）见 §4.6；第一人称粒子（`FirstPersonParticleSystem`）见 §4.7。判据见 §4.1

---

## 目录

1. [背景与目标](#1-背景与目标)
2. [现状与缺口](#2-现状与缺口)
3. [参考实现：SimpleBedrockModel 的做法](#3-参考实现simplebedrockmodel-的做法)
4. [目标设计](#4-目标设计)
5. [关键代码](#5-关键代码)
6. [实施阶段](#6-实施阶段)
7. [验证方案](#7-验证方案)
8. [风险与对策](#8-风险与对策)
9. [不变部分清单](#9-不变部分清单)
10. [待审阅决策点](#10-待审阅决策点)
11. [实施进度](#11-实施进度)
- [附录 A：单位与公式推导](#附录-a单位与公式推导)
- [附录 B：与 SBM 的差异对照](#附录-b与-sbm-的差异对照)
- [附录 C：工作量估算](#附录-c工作量估算)
- [修订记录](#修订记录)

---

## 1. 背景与目标

### 背景

`particle_soa_plan.md` 将「粒子碰撞（方块碰撞）」列入 Phase 2 的组件体系，Phase 4 的运行时也已落地——`minecraft:particle_motion_collision` 组件能正确**反序列化**并进入每 tick 的组件管线。但**运行时实现只是最小可行的打点采样版**：判定粒子中心点所在方块是否空气，命中则把三轴速度整体取反。它既不使用 `collision_radius`，也不修正粒子位置，更没有与位移积分对齐时序。

当前唯一的两个实际用例 `rain.particle.json:42-45`、`snow.particle.json:44-46` 都带 `collision_radius`，即该组件的**主用路径本身就未被正确实现**。

### 目标

把粒子与地形的交互从「打点采样 + 速度取反」升级为「**AABB 扫掠 + 逐轴反弹 + 位移裁剪（不穿透）**」，语义对齐 Bedrock 规范与原版 `Particle.move` / `Entity.collideBoundingBox` 碰撞内核，并适配 Spark-Core 的 SoA + 自绘渲染架构（不能继承原版 `Particle`，因此不能直接复用 `move()` 覆写这一手法）。

### 范围

| 纳入 | 不纳入 |
|------|--------|
| `minecraft:particle_motion_collision` 运行时重写 | 与**实体**的碰撞（基岩版该组件本就只碰方块） |
| 位移积分与碰撞的时序重排 | GPU 侧碰撞、物理引擎（Bullet）联动 |
| `collision_radius` / `collision_drag` / `coefficient_of_restitution` / `expire_on_contact` 全字段生效 | 粒子推动方块、破坏地形 |
| `events` + `min_speed` 碰撞事件触发 | 服务端粒子同步（沿用 particle_soa_plan 的 stub 边界） |
| （可选）逐粒子方块光照采样 | 改回原版 `Particle` 管线（与 SoA 架构冲突，见 §4.1） |
|  | 未加载区块的碰撞（客户端固有边界，见 §8） |
|  | **局部空间粒子**（`emitter_local_space.position=true`）的碰撞——明确**不参与**（§4.6） |
|  | **脚本粒子**（`particle_motion_parametric`，位置相对发射器）的碰撞——明确**不参与**（§4.6） |
|  | **第一人称粒子**（`FirstPersonParticleSystem`）的碰撞——明确**不参与**（§4.7） |
|  | 已重叠粒子（出生在方块内 / `radius` 变大）的**脱困**——有意偏离 Bedrock 规范，见 §4.4 与 D9 |

---

## 2. 现状与缺口

> 本章与 §1「背景」记录的是**计划创建时（2026-09-30）**的代码状态，是后续设计的依据；各项缺口的修复情况与验证覆盖面见 §11 实施进度。

### 2.1 缺口清单

| # | 缺口 | 代码位置 | 影响 |
|---|------|---------|------|
| **G1** | 碰撞判定使用**积分前**的旧位置，且命中后从不修正位置 | `ParticleEmitterInstance.java:400-402`（组件 tick）、`:416`（`integrateParticle`）、`ParticleArray.java:263-288` | 粒子先陷入方块一个 tick 的行程（雨滴约 0.8~1.2 格）；默认 `restitution=0` 时速度直接归零 → **粒子永久冻结在地表内部** |
| **G2** | `collision_radius` 编译后**从未求值** | `ParticleComponentRuntimes.java:482` | 半径完全无效，半径 0.1 与半径 1.0 行为一致 |
| **G3** | 反弹是**三轴整体取反** | `ParticleComponentRuntimes.java:513-516` | 落地时水平速度被反向（粒子倒退），贴墙时垂直速度被反向（粒子被墙弹飞） |
| **G4** | 判定为**单点采样** `BlockPos.containing` + `isAir()` | `ParticleComponentRuntimes.java:502-503` | 半砖/楼梯/栅栏等非整方块判定错误；薄墙与高速粒子直接穿透；无扫掠 |
| **G5** | 局部空间坐标被当作世界坐标查询方块 | `ParticleComponentRuntimes.java:494-503` vs `ParticleEmitterInstance.java:225,255` | `emitter_local_space.position=true` 的发射器碰撞结果完全随机；本计划裁定该组合**不参与碰撞**（§4.6），由构建期门控把这一类错误整体消除，而不是去实现跨坐标系扫掠 |
| **G6** | `events` 以 `getAsJsonObject()` 解析，且运行时从不使用 | `ParticleMotionCollision.java:47` | 数组格式（SBM/Bedrock 基线）会抛 `IllegalStateException` → **整个粒子定义加载失败**，仅留下一条 error 日志（`ParticleModule.kt:49-55`），游戏内表现为该粒子效果整体缺失 |
| **G7** | 无碰撞事件触发，`min_speed` 阈值未实现 | `ParticleComponentRuntimes.java:477-520` | 碰撞事件形同虚设 |
| **G8** | 全部粒子共用摄像机位置的光照 | `ParticleVisualEffectRenderer.java:68` | 粒子不随所处地形明暗变化（附带项，见 §4.8） |

### 2.2 关键：时序错位

`ParticleEmitterInstance.updateParticles()` 的现有顺序：

```
① 寿命过期判定
② Molang 绑定 + 曲线求值
③ 粒子组件 tick（含碰撞检测：读旧 pos，改 vel）
④ 时间线事件
⑤ buf.integrateParticle(i, tickDt)   ← 位置在这里才推进
```

碰撞组件（`order()=310`）在 ③ 中依据**上一 tick 结束时的位置**判定，命中后把速度取反，位移在 ⑤ 才发生。因此：

- 粒子进入方块的**那一刻不会被检测到**，要等到下一 tick 中心点仍在方块内才触发；
- 触发时位置不动，速度被取反/清零 → 默认参数下粒子**卡在方块里**；
- 若 `coefficient_of_restitution > 0`，则每 tick 进出一次，视觉上抖动。

这与 Bedrock 语义（粒子抵达方块表面即被挡住）存在一个 tick 的行程误差，且没有任何位移裁剪。

---

## 3. 参考实现：SimpleBedrockModel 的做法

SBM 的地形交互**不在 Molang 组件管线里**，而在世界粒子类的 `move()` 覆写里。它把「位移」这一步整个交给原版引擎。

> 本节描述已对照 SBM `1.21.1-dev@c314b1e` 逐行核对（`SnowStormParticle.java`、`ParticleMotionCollision.java`、`WorldEmitterManager.java`）：行号、时序与逐轴公式均与检出源码一致。

### 3.1 架构

| 表示 | 角色 |
|------|------|
| `ParticleInstance` | 纯数据 + Molang 组件管线，**不接触 Level** |
| `SnowStormParticle extends TextureSheetParticle` | 由原版 `ParticleEngine` 驱动，**唯一接触世界的对象** |

`WorldEmitterManager.deliverParticle()`（`WorldEmitterManager.java:141-176`）在把粒子投递给 `ParticleEngine` 前，**先把局部坐标换算成世界坐标并覆写 `particle.x/y/z`**，因此碰撞永远在世界坐标系中进行。

### 3.2 每 tick 时序（`SnowStormParticle.java:131-185`）

```
① 重算 collision.enabled 表达式 → hasCollision
② 保存旧位置 savedX/Y/Z
③ emitter.updateSingleParticle(particleData, dt)
     → 组件 update（只更新速度/外观）
     → ParticleInstance.tick(dt) 顺手积分了位置
④ 还原位置为 savedX/Y/Z        ← 丢弃第③步积分结果
⑤ xd/yd/zd = particleData.vx/20f …（方块/秒 → 方块/tick）
⑥ this.move(xd, yd, zd)        ← 唯一一次位移，内部做 AABB 扫掠
⑦ 把 move() 后的 x/y/z 与 xd/yd/zd 反写回 particleData
```

**关键设计**：组件管线只负责算速度，位置推进由原版 `move()` 唯一负责一次。碰撞就是积分本身，不存在「先判后动」的错位。

### 3.3 `move()` 的碰撞响应（`SnowStormParticle.java:187-241`）

- 构造时 `hasPhysics = true`，包围盒按半径膨胀：`this.setBoundingBox(this.getBoundingBox().inflate(radius))`（`SnowStormParticle.java:109-111`）。注意原版粒子的包围盒是 **0.2 立方但底部锚定**：`Particle.setPos` 构造的是 `[x±0.1, y … y+0.2, z±0.1]`（`Particle.java:178-187`，`setLocationFromBoundingbox` 也把 `y` 取 `minY`），膨胀后 Y 范围是 `[y-r, y+0.2+r]`。**该箱在三个方向上的半边长都是 `0.1 + r`（Y 方向亦然），只是箱心在 `y + 0.1` 而不是 `y`**；本计划改用**以粒子坐标为中心**的箱（半边长同为 `0.1 + r`），与 Spark-Core 自绘渲染的视觉中心一致，相对 SBM 整体低 0.1 格（等尺寸、仅锚点不同）—— 这是**有意的偏离**，见 D3 与附录 B；
- `Entity.collideBoundingBox(null, delta, box, level, List.of())` 返回**逐轴裁剪后的位移**，位置直接设为该结果（`:214-216`）→ **不穿透**；
- 逐轴响应（`:204-212`）：

  | 轴 | 公式（SBM 内部 v 单位为方块/tick） |
  |---|---|
  | X | `xd = -sign(xd) * (\|xd\| - collisionDrag/20) * restitution` |
  | Y | `yd *= -restitution`（**未减 drag**） |
  | Z | 同 X |

- `velSqr < 10000` 才做扫掠（原版惯用的速度上限保护，避免 `expandTowards` 产生超大 AABB）；
- 碰撞发生 → `onGround` 标记、`fireCollisionEvents(particleData, speed)`（按 `min_speed` 过滤）、`expireOnContact` 时 `remove()`。

### 3.4 SBM 自身的不完善处（不建议照抄）

| 项 | 说明 |
|---|---|
| `collisionDrag` 只在 X/Z 生效 | Y 轴只乘 restitution，疑似照搬原版 `Particle.move` 的 X/Z-only 写法 |
| `\|v\| < drag` 时符号翻转 | `-sign(v) * (\|v\| - drag) * r` 在 drag 大于速度时会**反向弹射** |
| 碰撞盒与视觉尺寸无关 | 恒为 0.2 立方 + radius，不使用 billboard 的 `width/height` |
| `expire_on_contact` 只 `remove()` | 未置 `particleData.alive = false`，emitter 侧记录不结束 |
| 数值字段不支持 MoLang | `getAsFloat()` 直读；Spark-Core 现有实现更贴基岩规范（见 §4.5） |
| 局部空间 | 投递时一次性转世界坐标，粒子此后不再跟随发射器 |
| 事件重复触发 | 接触期间每 tick 都判为碰撞并调用 `fireCollisionEvents`（`onGround` 只记录、不用来短路扫掠，`SnowStormParticle.java:226-240`）→ 本计划用接触位去重，见 D8 |

---

## 4. 目标设计

### 4.1 总体思路

**判据（谁参与碰撞）**：只有**在世界坐标中仿真**的粒子才参与方块碰撞。粒子若在另一个坐标系里仿真（实体/发射器空间），或本身就是摄像机空间的装饰层，就不存在与之碰撞的地形。这不是本文档的发明，而是 Bedrock 规范自身的定义：

- `minecraft:emitter_local_space` 把 `position=true` 定义为「particles will **simulate in entity space**」，而 `position=false`（缺省）才是「emit relative to the emitter, then **simulate independently from the emitter**」；该组件「**applies only when the emitter is attached to an entity**」，并规定 `rotation=true, position=false` 为非法组合；
- `minecraft:particle_motion_collision` 只被定义为「collisions between **the terrain and the particle**」——地形是世界属性，entity space 里没有地形；
- `minecraft:particle_motion_parametric` 的位置是「**relative to the emitter**」，且该组件「**won't work for … entity-based particle emitters that aren't in local space**」——它天生属于发射器坐标系。

因此本计划**不实现跨坐标系扫掠**，改为在**构建期**把这两类粒子的碰撞组件降级为 no-op（§4.6）；第一人称由**发射器实例开关**排除（§4.7）。想「跟随实体 + 与地形交互」的正确做法是缺省的世界空间发射器 + `bindToAnchor`（§4.6）。

Spark-Core 的粒子是 SoA 数组 + 自绘渲染，**没有原版 `Particle` 对象**，因此不能复制 SBM 的「覆写 `move()`」手法。但可以复用同一套**原版碰撞内核**：

```
SBM:  Particle.move()  →  Entity.collideBoundingBox(null, delta, box, level, List.of())
本计划: CollisionResolver.move()  →  同一个静态方法
```

差异只在于：位置与速度从 `ParticleArray` 的数组槽位读写，而不是 `Particle` 的字段。

**核心改动四件**：

1. **时序重排**——把「位移」从组件管线中抽出，作为所有组件 tick 之后的独立阶段，碰撞在该阶段内完成（§4.2）；
2. **参数 SoA 化**——碰撞组件退化为「每 tick 把 `radius/restitution/drag/flags` 写入数组」的参数写入器，扫掠由移动阶段统一执行（§4.3）；
3. **新增 `CollisionResolver`**——承载扫掠与逐轴反弹，是唯一调用原版碰撞内核的地方（§4.4）；
4. **收窄参与范围**——非世界空间粒子在构建期不用碰撞组件（§4.6），第一人称发射器在实例级跳过扫掠（§4.7）。

### 4.2 时序重排

`ParticleEmitterInstance.updateParticles()` 改为：

```
① 寿命过期判定                          （不变）
② Molang 绑定 + 曲线求值                （不变）
③ 粒子组件 tick                         （碰撞组件只写参数，不检测、不改速度与位置）
④ 时间线事件                            （不变）
⑤ CollisionResolver.move(i, tickDt, level, impact)  ← 新增：速度积分 + AABB 扫掠 + 逐轴反弹 + 位移裁剪
⑥ 按 ⑤ 的返回位标志分发：COLLIDED → 碰撞事件；EXPIRED → 过期事件 + pendingDead
```

**副作用评估**：

- `ParticleLifetimeKillPlane`(order 320) / `ParticleExpireIfInBlocks`(330) / `ExpireIfNotInBlocks`(340) 位于 ③，依据**移动前**位置判定；碰撞阶段（⑤）排在其后，因此它们的判定时机不受本计划影响、无需改动。SBM 同样是「组件 update → `move()` → 方块过期检查」，行为对齐。（是否改为「移动后判定」见 **D5**，本文档不含。）
- `snapshotPositions()` 仍在 tick 开头执行，`prevPos` 语义不变 → 渲染线程 lerp 正常，被墙挡住的粒子会平滑停在墙前。
- **直接写位置的组件不参与扫掠**：`ParticleMotionParametric`（`ParticleComponentRuntimes.java:445-471`）在 ③ 里 `setPos` 覆盖位置。这类粒子**整体不参与碰撞**（§4.6，D10 已裁定）：位置与速度都由表达式脚本拥有，扫掠唯一可能产生的后果是误触发碰撞事件与 `expire_on_contact`。既有的「组件直写位置，⑤ 再叠加一次 `vel * tickDt`」语义本文档不改变。
- **第一人称粒子不参与碰撞**（§4.7）：由 `ParticleEmitterInstance` 的实例级开关门控，⑤ 对它们只做无碰撞积分、不进入扫掠与碰撞事件。注意该开关**不能**依赖「`level` 为 null」——`FirstPersonEmitter.tick(level, …)` 传进来的正是 `mc.level`（`FirstPersonParticleSystem.java:101-110`），`ParticleEmitterInstance:173` 的 `molang.setLevel(level)` 因此非 null。

### 4.3 ParticleArray 扩展

新增 4 个并行数组（SoA 风格，与现有字段同构）：

| 数组 | 类型 | 含义 |
|------|------|------|
| `collRadius` | `float[]` | 碰撞半径（方块），来自 `collision_radius` 的每 tick 求值 |
| `collRestitution` | `float[]` | 弹性系数 |
| `collDrag` | `float[]` | 碰撞阻力（方块/秒，见附录 A） |
| `collFlags` | `byte[]` | `bit0 = COLL_ENABLED`、`bit1 = COLL_EXPIRE`、`bit2 = COLL_CONTACT`（上一 tick 是否贴住方块，供 D8 使用） |

> **⚠️ 四个同步点必须同时补齐**，漏一处即产生隐蔽 bug：
> `allocate()`（清零）、`grow()`（扩容）、`copySlotTo()`（compact 搬运）、`copyFrom()`（`ParticleDoubleBuffer.startTick()` 的深拷贝，`ParticleDoubleBuffer.java:46-49`）。
> 另外 `grow()` 需要为 `byte[]` **新增一个 `growArray` 重载**（现有只有 `float[]` / `boolean[]` 两个重载，`ParticleArray.java:165-175`）。
>
> **`copyFrom()` 漏改的真实表现是「读到 2 tick 前的陈旧值」**（tick 缓冲保留自己上一轮写入的内容，不会在 `startTick()` 时归零）；又因为 §4.5 的组件每 tick 都重写 `radius/restitution/drag/flags`，在该设计下几乎不可观测。仍必须补齐 —— 一旦将来改成「仅在 spawn 时写参数」，它立即变成真实缺陷。风险等级见 §8。

未声明碰撞组件的粒子 `collFlags = 0` → 走无碰撞快路径，**零额外开销**（绝大多数粒子属于此类）。

**门控复用同一条快路径**：局部空间粒子与脚本粒子在**构建期**就不创建碰撞组件（§4.6），`collFlags` 恒 0；第一人称发射器在**实例级**跳过 ⑤ 的扫掠（§4.7）。三类排除最终都落到「`collFlags = 0` + 无碰撞积分」，因此 `CollisionResolver` 里**不需要**第二套判定分支。

> **写冲突提醒**：`COLL_CONTACT` 由 `CollisionResolver` 写，而 `COLL_ENABLED` / `COLL_EXPIRE` 由组件每 tick 重写。组件若直接 `setCollFlags(index, flags)` 会把接触位清掉，因此组件必须**读-改-写**保留该位（§4.5、§5.1）。

### 4.4 `CollisionResolver`（新增）

职责：对单个粒子槽位执行「速度积分 → 位移 → 扫掠 → 反弹 → 写回 → 年龄」。

- **轴顺序无需自行实现（但不要写错）**：`Entity.collideBoundingBox` 内部按 **Y → （|Δx| 与 |Δz| 中较大的那一轴）→ 另一轴** 的顺序裁剪（`Entity.java:974-1009`：`flag = |Δx| < |Δz|` 为真时先裁 Z，否则先裁 X；相等时先 X）。逐行核对后的细节是：**先裁的那个水平轴会顺带把盒子移过去，后裁的那一轴只取值、不再移动盒子** —— `:989-994` 先裁 Z 并 `move`；`:996-1001` 后裁 X，其 `move` 被 `!flag` 守卫，故不移动；`:1003-1005` 后裁 Z 同样不移动。这是原版实体碰撞语义，**若采用方案 B/C 自建扫掠，必须照抄这一顺序与该细节**，否则贴墙滑行会与原版不一致；
- **不穿透 ≠ 脱困，且"已重叠"意味着直接穿过**：`collideBoundingBox` 只裁剪**本 tick 的位移**，且 `VoxelShape.collide` 只检查**盒子前锋面之外**的体素（`VoxelShape.java:238-278`：负向移动时从 `findIndex(min + 1e-7) - 1` 往下扫）。因此：
  - 盒子贴着表面（`box.min == 方块顶面`）时判定为**被挡住**（位移裁剪为 0）—— 静止粒子每 tick 都会命中，这是 D8 的由来；
  - 盒子已经**陷入**方块内部（出生在方块内、`radius` 变大、方块被放到粒子身上）时，该方块**根本不会阻挡它** —— 粒子会直接穿过去（原版与 SBM 同样如此），既不会被挡住也不会被推出。
  本计划沿用原版内核语义、不实现推出逻辑。**这是对 Bedrock 规范的有意偏离**：规范原文写的是碰撞「consists of detecting an intersection, **moving to a nearby non-intersecting point for the particle (if possible)**, and setting its direction to not be aimed towards the collision」，即**规范里有脱困**；本计划选的是原版 `Particle.move` 内核的行为（不脱困）。是否补脱困见 **D9**；§7.1 的「重叠出生」用例即验证这一行为；
- **分配代价**：`AABB` 与 `Vec3` 在原版中均不可变（`AABB.setMinX()` 系列也返回新对象，`AABB.java:71-93`）。一次扫掠实际产生约 **6~10 次**小对象分配：调用侧 `AABB` + `Vec3`、`expandTowards` 的 `AABB`、`collectColliders` 的 `ImmutableList`（builder + 底层数组 + 成品列表，`Entity.java:958-972`）、`getBlockCollisions` 的捕获 lambda 与 `BlockCollisions` 迭代器（`CollisionGetter.java:81-82`）、末尾的 `new Vec3`；**未命中也照常分配**。取舍见 **D1**，基准测试见 §7.3；
- **速度上限保护**：`dx²+dy²+dz² >= 10000` 时跳过扫掠（原版 `Particle.MAXIMUM_COLLISION_VELOCITY_SQUARED = Mth.square(100.0)`，`Particle.java:22,196`），防止 `expandTowards` 产生超大 AABB 引发 `getBlockCollisions` 遍历爆炸；
- **裁剪判定用 epsilon**：原版 `Shapes.collide` 对 `|desiredOffset| < 1.0E-7` 直接返回 `0.0`（`Shapes.java:222-232`，该判断在**形状列表非空**时逐形状生效；列表为空则原样返回），所以「返回值与输入不等」并不等价于「撞到了方块」（SBM 用浮点全等判断，存在幻影碰撞）。本计划用 `|clipped - delta| > 1e-7` 判定（§5.2）；
- **`collision_radius` 按规范夹取到 ≤ 0.5 格**：Bedrock 原文「note that this must be **less than or equal to 1/2 block**」。本计划在 `CollisionResolver` 内夹取（`MAX_COLLISION_RADIUS`），并对超限值给出一次性告警。不夹取的后果是把 §8「半径通常 ≤ 0.1 → AABB 覆盖 1~2 个方块」的性能假设交给作者笔误决定：一个 `collision_radius: 5` 就会让 `expandTowards` 造出超大 AABB，把 `getBlockCollisions` 的遍历炸掉。

### 4.5 碰撞组件改造

`ParticleComponentRuntimes.createMotionCollision()` 从「检测者」变为「参数写入者」。**注意**：在**非世界空间**预设里这个组件根本不会被创建（§4.6），所以下面的读-改-写不需要再判断坐标系：

```java
@Override
public void tick(ParticleArray buf, int index, float tickDt, ParticleMolangEnvironment ctx) {
    if (!buf.isAlive(index)) return;
    // 读-改-写：保留 CollisionResolver 写入的接触位，避免把它清掉
    byte flags = (byte) (buf.getCollFlags(index) & ParticleArray.COLL_CONTACT);
    if (molang.evaluate(enabledExpr) != 0) {
        flags |= ParticleArray.COLL_ENABLED;
        buf.setCollRadius(index, (float) molang.evaluate(radiusExpr));      // ← G2 修复
        buf.setCollRestitution(index, (float) molang.evaluate(restitutionExpr));
        buf.setCollDrag(index, (float) molang.evaluate(dragExpr));
    }
    if (expireOnContact) flags |= ParticleArray.COLL_EXPIRE;
    buf.setCollFlags(index, flags);
}
```

数值字段沿用 Spark-Core 的**全字段 MoLang 编译**能力（优于 SBM 的 `getAsFloat()` 直读）；`order()` 仍为 310（SBM 该组件为 350，顺序差异不影响语义）。

组件**只写参数、不做检测、不改速度与位置**；参数每 tick 全量重写 —— 这也是 §4.3 中 `copyFrom()` 漏改难以观察的原因。

### 4.6 局部空间粒子与脚本粒子：不参与碰撞

**结论（D2 已裁定）**：`emitter_local_space.position = true` 的发射器，以及声明了 `particle_motion_parametric` 的粒子，**不参与方块碰撞**。**不采用「在发射器坐标系里扫掠」**（即把局部位移变换到世界、扫掠后再变换回局部）的理由有三条：

1. **规范层面没有落点**：`position=true` 的粒子「simulate in **entity space**」，而碰撞组件被定义为「terrain 与 particle 之间」——entity space 里没有地形（§4.1 引原文）；
2. **这种做法本身不完整**：局部空间粒子的世界位移有**两项** —— 局部速度的世界像，以及**发射器这一 tick 的变换增量**。只扫掠前者会漏掉后者，而 `position=true` 唯一有意义的场景正是「附着到实体」（规范明示该组件 *applies only when the emitter is attached to an entity*），此时后者往往是主项，对旋转发射器它甚至不是刚体平移。要「做对」得扫掠 `T_t - T_{t-1}`，量级完全不同；
3. **实现风险/收益倒挂**：回变换的逐轴反弹必须作用在**世界速度分量**上、再变回局部速度存储（若按世界轴命中标志去反弹局部速度分量，含旋转/非均匀缩放时必错），而仓库内唯二使用 `emitter_local_space` 的预设（`snowstorm:magic`、`snowstorm:loading`，均为 `position:true, rotation:true` 的光环）**都没有碰撞组件**，没有任何消费方。

**实现方式（构建期门控）**：`ParticleEffectDeserializer.buildParticlePreset()` 同时能看到发射器组件（`buildEmitterPreset` 先于它执行，`ParticleEffectDeserializer.java:97-98`），因此：

- 若 `emitter_local_space.position = true`，**或**粒子组件里存在 `ParticleMotionParametric` → 碰撞组件**不创建**（返回 no-op），连每 tick 的 MoLang 求值都省掉；
- 加载期对「同时声明了二者」的预设各告警**一次**（不是每 tick），提示该组合下碰撞被忽略；
- 为此把 `worldSpace`（= `!hasLocalPosition` 且无 `ParticleMotionParametric`）作为参数传入 `buildParticlePreset`，与 §5.4 传 `CollisionEvent` 列表同一处。

**替代做法（能力不丢失）**：「跟随实体 + 与地形交互」应当用**世界空间发射器**：不写 `emitter_local_space.position`（即规范缺省的 "emit relative to the emitter, then **simulate independently**"），用 `bindToAnchor` 让发射器每 tick 跟随锚点。Spark-Core 已支持——`ParticleEmitterInstance.tick` 轮询锚点变换，`emitParticles` 只在 `!isLocal` 时把生成位置/速度变换到世界（`ParticleEmitterInstance.java:255-277`），新粒子此后独立仿真，碰撞天然正确。

> 静态发射器不是例外：渲染端乘进去的是**含平移的完整变换**（`ParticleRenderer.java:47-51`），所以局部坐标只有在「发射器位于原点且旋转/缩放为单位」时才等于世界坐标，没有值得保留的退化场景。
>
> 顺带记录两个既有问题（**不在本计划范围**，建议并入上游阻塞项）：`ParticleMotionParametric` 把 `relative_position` 当作绝对坐标直接 `setPos`（`ParticleComponentRuntimes.java:456-460`），既不要求 local space、也不叠加发射器变换，与上述两条规范都不符；`emitter_local_space.rotation` 解析后**全仓无人消费**（只有 `EmitterPreset.hasLocalRotation()` 这个 getter），而规范规定 `rotation=true, position=false` 为非法组合，应在加载期告警。

### 4.7 碰撞事件

- **解析修正**：`ParticleMotionCollision.fromJson` 的 `events` 同时接受**数组**与**单个对象**两种写法。基岩公开规范二者都给出——数组 `[{ "event": "...", "min_speed": 2 }]`，以及对象 `{ "event": "...", "min_speed": 2 }`（文档注明 "can be an individual event as well"）；`min_speed` 缺省 **2 方块/秒**（附录 A）。现用的 `getAsJsonObject()` 在数组格式下会抛异常并让整个定义加载失败（G6），而对象格式在现有实现中从未被读取（G7）。修正后：数组 → 逐项解析，对象 → 单元素列表，其余类型 → 空表而**不抛异常**（见 §5.4）；
- **触发时机与速度口径**：`CollisionResolver.move()` 以**位标志**返回本 tick 结果（`RESULT_COLLIDED` / `RESULT_EXPIRED` / `RESULT_CONTACT_EDGE`，见 §5.2），调用方在 `CONTACT_EDGE`（碰撞发生在接触上升沿）时用 `impact[0]`（= 积分后、反弹**前**的 `|v|`，方块/秒）过 `min_speed` 阈值。取反弹前速度与 SBM 一致：SBM 在 `move()` 内先改 `xd/yd/zd` 字段，再由**尚未回写**的 `particleData.vx/vy/vz` 计算 speed（`SnowStormParticle.java:204-235`）。`min_speed` 的口径由规范确认：官方文档写 "default/minimum is **2 blocks/sec**"，与计划的「2 / 方块每秒」一致；
- **触发入口**：`EventExecutor` 新增 `fireCollisionEvents(List<CollisionEvent> events, int particleIndex, ParticleArray buf, float speed)`，内部按 `min_speed` 过滤后复用现有的 `fireEventNames(List<String>, buf, index)`（`EventExecutor.java:60-69`）；
- **`expire_on_contact`**：致死时同样触发「粒子过期事件」（对齐 SBM `fireParticleExpirationEvents`）。同 tick 是否**也**触发碰撞事件（上升沿条件下两者会同时满足）见 **D11**；
- **重复触发**：静止/接触期间每 tick 都会判定为碰撞，因此用**接触位去重**（只在上升沿触发事件）；是否进一步短路扫掠见 **D8**；
- **不参与碰撞的范围（第一人称）**：第一人称粒子（`FirstPersonParticleSystem.FirstPersonEmitter`）明确**不做碰撞**，这是**产品决定**而非技术限制 —— 它们是视图层装饰（发射器位置每 tick 绑在摄像机上，`FirstPersonParticleSystem.java:101-110`），只对本人可见（服务端 stub），而摄像机可以处在方块内部（旁观/穿墙/`/tp`/水中），开碰撞会让贴墙的枪口火焰按 `expire_on_contact` 凭空消失或每 tick 抖动。实现上用**实例级开关**（`ParticleEmitterInstance.collisionEnabled`，`FirstPersonEmitter` 构造时置 false）门控 ⑤ 的扫掠；**不能**依赖 `level` 恰好为 null —— `FirstPersonEmitter.tick` 传入的是 `mc.level`，`ParticleEmitterInstance:173` 的 `molang.setLevel(level)` 因此非 null。该系统另有一处既有问题，与本决策无关且**不在本计划范围**：`super(def, null)`（`:93`）使 `EventExecutor` 持有的 `level` 为 null（`EventExecutor.java:143` 会静默丢弃 particle_effect 事件，`:199` 的 `level.isClientSide()` 可能 NPE）。另外 `FirstPersonParticleSystem` 当前**没有任何调用点**（上游 M4 记为 Phase 7 未完成），所以这条排除是前瞻性的，不涉及现有内容回归。

### 4.8 附带项：逐粒子方块光照（可选，P3）

SBM 在启用 `particle_appearance_lighting` 时用 `super.getLightColor(partialTick)`，即**按粒子位置**采样方块光照；Spark-Core 当前在 `ParticleVisualEffectRenderer.java:68` 取一次 `camera.getBlockPosition()` 的光照供所有粒子使用。

改为逐粒子采样需在渲染循环内调用 `LevelRenderer.getLightColor(level, BlockPos.containing(x,y,z))`，成本与粒子数线性相关。缓解方案：先用 `level.hasChunkAt(pos)` 短路未加载区块（原版 `Particle.getLightColor` 的做法，`Particle.java:230-233`），再加每帧一个 `Long2IntOpenHashMap` 的方块坐标→光照缓存（同一方块内的粒子只采样一次）。**列为可选阶段**，不阻塞 P0~P2。

> **`renderAll()` 的 light 泄漏已修复（2026-10-02，见 §11.1）**：缺陷是在 `ParticleRenderer.renderAll()` 的循环内就地改写 `light` 入参，一旦遇到一个未启用 `particle_appearance_lighting` 的发射器，其后所有发射器都会被渲染成 `FULL_BRIGHT`；现已改为逐发射器计算独立变量。P3 改到这段代码时无需重复处理。

---

## 5. 关键代码

### 5.1 `ParticleArray` 新增字段与同步点

```java
// ====== 碰撞参数（每 tick 由 ParticleMotionCollision 运行时组件写入）======
private float[] collRadius;       // 碰撞半径（方块）
private float[] collRestitution;  // 弹性系数
private float[] collDrag;         // 碰撞阻力（方块/秒）
private byte[]  collFlags;        // bit0 = 启用, bit1 = 接触即死亡, bit2 = 接触状态

public static final byte COLL_ENABLED = 1;
public static final byte COLL_EXPIRE  = 1 << 1;
public static final byte COLL_CONTACT = 1 << 2;   // 由 CollisionResolver 写，组件读-改-写保留

// allocate():
collRadius[slot] = 0; collRestitution[slot] = 0; collDrag[slot] = 0; collFlags[slot] = 0;

// grow():        collRadius = growArray(collRadius, newCapacity); … collFlags = growArray(collFlags, newCapacity);
//                ⚠ 需新增 byte[] 重载（现有只有 float[] / boolean[] 两个重载）：
//                private static byte[] growArray(byte[] src, int newCapacity) { … System.arraycopy … }
// copySlotTo():  collRadius[dst] = collRadius[src]; … collFlags[dst] = collFlags[src];
// copyFrom():    System.arraycopy(src.collRadius, 0, collRadius, 0, srcCount); …
//                System.arraycopy(src.collFlags,  0, collFlags,  0, srcCount);
```

> `CollisionResolver` 先算完三轴再一次性 `setVel(i, vx, vy, vz)`，因此**不需要**新增逐轴 `setVelX/Y/Z`；不要为对称性添加无用 API。

### 5.2 `CollisionResolver`（新文件）

```java
package cn.solarmoon.spark_core.particle.client;

/**
 * 粒子位移与方块碰撞扫掠。取代 ParticleArray.integrateParticle() 成为唯一的位置推进点。
 * <p>
 * 所有粒子速度单位为 方块/秒，tickDt 固定 0.05（ParticleVisualEffectRenderer.java:34）。
 * 仅在 (collFlags & COLL_ENABLED) != 0 且 Level 可用时执行扫掠，其余粒子走无碰撞快路径。
 * <p>
 * 语义基线：Bedrock particle_effect v1.10.0 + 原版 Particle.move / Entity.collideBoundingBox。
 */
public final class CollisionResolver {

    private CollisionResolver() {}

    /** 原版 Particle 包围盒半边长（0.2 立方）；本计划以粒子坐标为中心，见 D3 */
    private static final float BASE_HALF = 0.1f;
    /** collision_radius 的规范上限：基岩要求 ≤ 1/2 格（§4.4） */
    private static final float MAX_COLLISION_RADIUS = 0.5f;
    /** 与原版 Particle.MAXIMUM_COLLISION_VELOCITY_SQUARED 同源：位移平方超过此值不做扫掠 */
    private static final double MAX_MOVE_SQR = 10000.0;
    /** 裁剪判定阈值：原版 Shapes.collide 对 |delta| < 1e-7 的分量直接返回 0，不能用全等比较 */
    private static final double CLIP_EPSILON = 1.0E-7;

    /** 本 tick 发生轴向裁剪 */
    public static final byte RESULT_COLLIDED = 1;
    /** 因 expire_on_contact 致死（调用方需触发过期事件） */
    public static final byte RESULT_EXPIRED = 1 << 1;
    /** 碰撞发生在接触位的上升沿（上一 tick 未贴住方块）→ 碰撞事件应触发 */
    public static final byte RESULT_CONTACT_EDGE = 1 << 2;

    /**
     * 单粒子运动积分 + 扫掠 + 反弹。
     *
     * @param impact 调用方复用的长度 ≥ 1 暂存器；发生碰撞时写入反弹前的 |v|（方块/秒），供 min_speed 过滤
     * @return 位标志组合：{@link #RESULT_COLLIDED} / {@link #RESULT_EXPIRED} / {@link #RESULT_CONTACT_EDGE}
     */
    public static byte move(ParticleArray buf, int i, float tickDt, @Nullable Level level, float[] impact) {
        if (!buf.isAlive(i)) return 0;

        // ── 1. 速度积分（公式与 ParticleArray.integrateParticle 逐位等价，P0 验收项）──
        float vx = buf.getVelX(i) + buf.getAccelX(i) * tickDt;
        float vy = buf.getVelY(i) + buf.getAccelY(i) * tickDt;
        float vz = buf.getVelZ(i) + buf.getAccelZ(i) * tickDt;
        float dragFactor = Math.max(0f, 1f - buf.getDragCoeff(i) * tickDt);
        vx *= dragFactor; vy *= dragFactor; vz *= dragFactor;

        // ── 2. 待位移（float 计算：无碰撞路径与 ParticleArray.integrateParticle 逐位等价，P0 验收项）──
        float fx = vx * tickDt, fy = vy * tickDt, fz = vz * tickDt;

        // ── 3. 方块扫掠（D8 选 B 时仍每 tick 执行；轴顺序由原版内核负责，勿自行实现）──
        byte result = 0;
        byte flags = buf.getCollFlags(i);   // 组件本 tick 已读-改-写，接触位可用
        if (level != null && (flags & ParticleArray.COLL_ENABLED) != 0
                && (double) fx * fx + (double) fy * fy + (double) fz * fz < MAX_MOVE_SQR) {

            // collision_radius 按规范夹取到 ≤ 0.5 格（§4.4）：不夹取时一个笔误就能造出超大 AABB
            float half = BASE_HALF + Math.min(buf.getCollRadius(i), MAX_COLLISION_RADIUS);
            double dx = fx, dy = fy, dz = fz;
            double px = buf.getPosX(i), py = buf.getPosY(i), pz = buf.getPosZ(i);
            AABB box = new AABB(px - half, py - half, pz - half,
                                px + half, py + half, pz + half);

            Vec3 clipped = Entity.collideBoundingBox(null, new Vec3(dx, dy, dz),
                                                     box, level, List.of());

            boolean hitX = Math.abs(clipped.x - dx) > CLIP_EPSILON;
            boolean hitY = Math.abs(clipped.y - dy) > CLIP_EPSILON;
            boolean hitZ = Math.abs(clipped.z - dz) > CLIP_EPSILON;

            if (hitX || hitY || hitZ) {
                result |= RESULT_COLLIDED;
                impact[0] = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);   // 反弹前速度
                float restitution = buf.getCollRestitution(i);
                float cDrag = buf.getCollDrag(i);
                if (hitX) vx = bounce(vx, cDrag, restitution);
                if (hitY) vy = bounce(vy, cDrag, restitution);
                if (hitZ) vz = bounce(vz, cDrag, restitution);
                // 未被裁的轴 clipped 与输入逐位相同，故回写 float 不改变快路径结果
                fx = (float) clipped.x; fy = (float) clipped.y; fz = (float) clipped.z;
            }
        }

        // ── 4. 写回位置与速度 ──
        buf.setVel(i, vx, vy, vz);
        buf.setPos(i, buf.getPosX(i) + fx,
                      buf.getPosY(i) + fy,
                      buf.getPosZ(i) + fz);

        // ── 5. 旋转与年龄（公式同 ParticleArray.integrateParticle）──
        float rotRate = buf.getRotRate(i) + buf.getRotAccel(i) * tickDt;
        buf.setRotRate(i, rotRate);
        buf.setRot(i, buf.getRot(i) + rotRate * tickDt);
        buf.setAge(i, buf.getAge(i) + tickDt);

        // ── 6. 接触位（D8 选 B：仅用于碰撞事件去重）──
        if ((result & RESULT_COLLIDED) != 0) {
            if ((flags & ParticleArray.COLL_CONTACT) == 0) result |= RESULT_CONTACT_EDGE;
            flags |= ParticleArray.COLL_CONTACT;
        } else {
            flags &= ~ParticleArray.COLL_CONTACT;
        }
        buf.setCollFlags(i, flags);

        // ── 7. expire_on_contact ──
        if ((result & RESULT_COLLIDED) != 0 && (flags & ParticleArray.COLL_EXPIRE) != 0) {
            buf.markDead(i);
            result |= RESULT_EXPIRED;
        }
        return result;
    }

    /**
     * 逐轴碰撞响应。v 与 drag 单位均为 方块/秒。
     * 对速度下限做 0 截断：drag 大于速度时不反向弹射（优于 SBM，见附录 B）。
     * 注：1.21.1 的 Mth 只有 {@code sign(double) -> int}（Mth.java:690），此处依赖 int→float 提升。
     */
    private static float bounce(float v, float drag, float restitution) {
        float magnitude = Math.max(0f, Math.abs(v) - drag);
        return -Mth.sign(v) * magnitude * restitution;
    }
}
```

> **方案 B / C（决策点 D1）**：
> **(B) 自建扫掠**——用 `level.getBlockCollisions(null, box.expandTowards(dx,dy,dz))` 取得 `Iterable<VoxelShape>`，再逐轴调用 `Shapes.collide(Direction.Axis, AABB, Iterable<VoxelShape>, double)`（`Shapes.java:222`），省掉 `Vec3` 与 `collideBoundingBox` 内部的 `ImmutableList` 分配（`AABB` 与 `BlockCollisions` 仍不可省，见 §4.4）。**自建时轴顺序必须照抄 `Entity.java:974-1009`：Y → 位移较大的水平轴 → 另一轴；先裁的水平轴会把盒子移过去，后裁的那一轴只取值、不移动盒子（`:989-1005`）。**
> **(C) 空形状预筛（建议优先于 B）**——先用**无分配**的预筛判断本次位移可能触及的 1~4 个方块是否可能有碰撞形状（`getCollisionShape(...)` 非空 / 非空气），只有预筛命中才调用内核。绝大多数粒子在空气中，这条路径可把每 tick 的分配从数万压到接近 0；而 B 只省下 `Vec3` 与 `ImmutableList` 两项，收益有限。§7.3 的基准应先量「预筛命中率」，再决定是否切 C。
>
> A / B / C 三种实现都必须保留 `MAX_COLLISION_RADIUS` 夹取与 `MAX_MOVE_SQR` 保护。

### 5.3 `ParticleEmitterInstance.updateParticles()` 调整

```java
// ParticleEmitterInstance 新增字段：
private final float[] impactScratch = new float[1];   // 每个发射器一个复用暂存器，零分配
private boolean collisionEnabled = true;              // 实例级门控；FirstPersonEmitter 构造时置 false（§4.7）

// ④ 时间线事件之后、位置推进所在的位置：
// 非世界空间粒子（局部空间 / parametric）在构建期就没有碰撞组件，collFlags 恒 0（§4.6），
// 会自动走 CollisionResolver 的无碰撞快路径；此处只需再过一道实例级开关（第一人称）。
byte result = CollisionResolver.move(buf, i, tickDt,
        collisionEnabled ? level : null, impactScratch);   // level == null ⇒ 只做积分，不扫掠
if ((result & CollisionResolver.RESULT_CONTACT_EDGE) != 0) {
    eventExecutor.fireCollisionEvents(preset.getCollisionEvents(), i, buf, impactScratch[0]);
}
if ((result & CollisionResolver.RESULT_EXPIRED) != 0) {
    eventExecutor.fireExpirationEvents(i, buf);   // expire_on_contact 等价于过期
    pendingDead.add(i);
}
// 删除 buf.integrateParticle(i, tickDt) 调用：位置推进只由 CollisionResolver.move() 执行一次。
// ParticleArray.integrateParticle(int, float) / integrate(float) 也随之删除：
//   integrate(float) 在现有代码中没有任何调用者（死代码；仅 ParticleDoubleBuffer.java:42 的
//   javadoc 提到它，需一并更新），integrateParticle 的积分公式已整体迁入 CollisionResolver.move()。
//   **只保留一条积分路径** —— 两条路径必然分叉。
// 若 D8 最终选 C（不做事件去重），此处改判 RESULT_COLLIDED。
```

> `impactScratch` 是逐发射器复用的 `float[1]`：只在发生碰撞时写入，调用方紧随其后读取，零分配。不要为此新增第 5 个 SoA 数组（那会把 §4.3 的同步点从 4 个变成 5 个）。
>
> `preset` 取自 `definition.getParticlePreset()`（与现有 `findLifetimeEvents()` 同一处），且必须走 §5.4 的预取字段——`ParticleEmitterInstance:405` 已经在每粒子每 tick 调 `findLifetimeEvents()`，碰撞事件不要在热路径上再加一次同类反查。
>
> `collisionEnabled` 默认 `true`，是唯一需要感知「第一人称」的地方；`FirstPersonEmitter` 在构造时置 false 即可，不需要让 `CollisionResolver` 知道任何发射器类型。

### 5.4 `ParticleMotionCollision` 的 `events` 解析修正

```java
private static List<CollisionEvent> parseEvents(JsonObject obj) {
    // 数组与单对象两种写法都合法（基岩规范二者都给出）；其余类型返回空表，绝不抛异常（G6）
    if (!obj.has("events")) return List.of();
    JsonElement element = obj.get("events");
    List<CollisionEvent> list = new ArrayList<>();
    if (element.isJsonArray()) {
        for (JsonElement item : element.getAsJsonArray()) addEvent(item, list);
    } else {
        addEvent(element, list);
    }
    return list;
}

private static void addEvent(JsonElement element, List<CollisionEvent> out) {
    if (!element.isJsonObject()) return;
    JsonObject o = element.getAsJsonObject();
    String event = o.has("event") ? o.get("event").getAsString() : "";
    float minSpeed = o.has("min_speed") ? o.get("min_speed").getAsFloat() : 2f;  // 缺省 2 方块/秒
    if (!event.isEmpty()) out.add(new CollisionEvent(event, minSpeed));
}
```

解析结果在 `ParticleEffectDeserializer` 里提取到 `ParticlePreset`（与 `faceCameraMode` / `hasLighting` 同一模式，`ParticleEffectDeserializer.java:216-257`），供 ⑤ 之后的触发点直接读取，避免每 tick 反查组件。**同一处**顺带把 §4.6 的 `worldSpace` 标志（`!hasLocalPosition` 且无 `ParticleMotionParametric`）传下去，用于构建期门控碰撞组件；`ParticlePreset` 已经保留 `definitions` 列表（`ParticlePreset.java:18,36-43`），也可以选择沿用 `findLifetimeEvents()` 的写法加一个缓存字段，二者取其一即可。

`EventExecutor` 相应新增（内部复用现有的 `fireEventNames(List<String>, buf, index)`，`EventExecutor.java:60-69`）：

```java
public void fireCollisionEvents(List<CollisionEvent> events, int particleIndex,
                                ParticleArray buf, float speed) {
    if (events == null || events.isEmpty()) return;
    List<String> names = null;
    for (CollisionEvent e : events) {
        if (speed < e.minSpeed()) continue;
        if (names == null) names = new ArrayList<>(events.size());
        names.add(e.event());
    }
    if (names != null) fireEventNames(names, buf, particleIndex);
}
```

> `events` **两种写法都接受**：官方文档同时给出数组与对象两种形式（对象注明 "can be an individual event as well"），所以「只认数组」反而是偏离规范。对象格式在现有实现中从未被读取（G6/G7），两种都收既不牺牲任何现存功能，又修掉了现用 `getAsJsonObject()` 在数组格式下抛异常、导致整个定义加载失败的问题。「其余类型视为空表且不抛异常」是兜底，不是主路径。

---

## 6. 实施阶段

### P0 — 基础设施（无可见行为变化）

| 项 | 内容 |
|---|---|
| 文件 | `ParticleArray.java`、`CollisionResolver.java`（骨架）、`ParticleEmitterInstance.java`、`ParticleDoubleBuffer.java`（javadoc） |
| 产出 | 4 个碰撞数组 + 4 个同步点（含 `byte[]` 版 `growArray` 重载）+ 位标志常量；`CollisionResolver.move()` 先实现为 `integrateParticle` 的**逐位等价**版本（返回 0、不启用扫掠）；删除 `ParticleArray.integrate(float)` 死代码 |
| 验证 | `collFlags` 恒 0（碰撞未启用）时，`CollisionResolver` 的积分结果与 `ParticleArray.integrateParticle` **逐位一致**：加载 `rain`/`snow` 并逐帧比对轨迹（**前提**：无碰撞路径保持 float 运算，见 §5.2，否则这一条不可能成立）；跨 tick 参数不丢失（连续 100 tick 断言 `collFlags` / `collRadius`） |
| 回滚 | 删除新文件，恢复 `integrateParticle` 调用点 |

### P1 — 扫掠碰撞（核心）

| 项 | 内容 |
|---|---|
| 文件 | `CollisionResolver.java`、`ParticleComponentRuntimes.java`、`ParticleEmitterInstance.java`、`ParticleEffectDeserializer.java`（构建期门控）、`FirstPersonParticleSystem.java`（实例开关） |
| 产出 | 启用 `Entity.collideBoundingBox` 扫掠；逐轴反弹 + `max(0, …)` 截断；epsilon 裁剪判定；`collision_radius` 生效并按规范夹取到 ≤ 0.5；`expire_on_contact` 生效；接触位与碰撞事件去重（D8）；组件退化为参数写入器（§4.5，读-改-写保留接触位）；**构建期门控**——`emitter_local_space.position=true` 或含 `ParticleMotionParametric` 的预设不创建碰撞组件，并各告警一次（§4.6）；**实例级门控**——`collisionEnabled=false` 的第一人称发射器只做无碰撞积分（§4.7） |
| 验证 | 雨/雪落到地表时停止在表面、不进入方块；`/spark particle spawn` 对墙测试（见 §7.1）；`bounce()` 白盒单测；`magic`/`loading` 的光环外观与运动不受影响，且各产生一条门控告警；第一人称发射器不进入扫掠；**静止粒子**（按 §7.3 造到 4000 并发）的每 tick 耗时与分配量 |
| 回滚 | 组件内把 `collFlags` 恒写 0 → 全部退回无碰撞路径 |

### P2 — 事件与解析修正

| 项 | 内容 |
|---|---|
| 文件 | `ParticleMotionCollision.java`、`ParticleEffectDeserializer.java`、`EventExecutor.java`、`ParticleEmitterInstance.java` |
| 产出 | `events` 数组/对象**双格式**解析 + `min_speed`（缺省 2 方块/秒）触发（§4.7、§5.4，含 `EventExecutor.fireCollisionEvents`）；`expire_on_contact` 触发过期事件（同 tick 是否兼触发碰撞事件见 D11） |
| 验证 | 构造带 `events` 的测试 JSON（数组、对象各一份）验证触发；低速接触不触发、高速接触触发、静止期不重复触发；`min_speed` 用已知速度做一次标定（附录 A） |
| 回滚 | `parseEvents` 回退为只读数组（丢对象格式，但不会崩溃） |

### P3 — 逐粒子方块光照（可选）

| 项 | 内容 |
|---|---|
| 文件 | `ParticleVisualEffectRenderer.java`、`ParticleRenderer.java` |
| 产出 | 按粒子位置采样方块光照 + `hasChunkAt` 短路 + 每帧方块坐标缓存（§4.8）。`renderAll()` 的 light 泄漏已在此之前修复（§11.1），本阶段不再包含它 |
| 验证 | 洞穴内粒子变暗；性能回归 < 5% |

---

## 7. 验证方案

### 7.1 手动场景

**id 口径**：内容包 JSON 里的 `description.identifier`（本文件写作 `snowstorm:rain` 等）**不是**命令与注册表使用的键。注册键由内容包目录推导：`spark_modules/spark_core.default/minecraft/particles/rain.particle.json` → `minecraft:rain`。因此 `/spark particle spawn` 应写 `minecraft:rain`、`minecraft:magic`（`/spark particle list` 输出的是这些键）。

**主用例**：默认内容包自带测试粒子 `minecraft:collision_test`（`collision_test.particle.json`，基于 `snow` 改出）——单个橙色球，初速 6 格/秒垂直向下、重力 9.8、`collision_radius 0.15`、`collision_drag 1.5`、`restitution 0.6`、`expire_on_contact false`，并带两级 `min_speed`（2 / 8）的数组格式碰撞事件。它的**视觉半边长与碰撞盒半边长相等（0.25）**，所以静止时贴图正好落在方块表面上，肉眼即可判断有没有陷入方块；`collision_radius` 一改，贴图与碰撞盒就不再重合，便于观察半径是否真的生效。

| 场景 | 步骤 | 期望 |
|---|---|---|
| **弹跳球（主用例）** | `/spark particle spawn minecraft:collision_test ~ ~6 ~`（离地约 6 格） | 落地弹跳约 4 次并逐次变矮（`drag` 生效）；每次接触在日志出现 `[collision_test] 碰撞…`，其中反弹前 \|v\| ≥ 8 的那次接触额外出现 `强碰撞` 日志与一次石头撞击音效；最终**静止**在地表上方 0.25 格（贴地、不下沉、不抖动），静止期间**不再**产生新日志/音效（接触上升沿去重） |
| 落地即消失（`expire_on_contact`） | 把 `collision_test` 的 `expire_on_contact` 改为 `true`（或用 `minecraft:rain`） | 接触当 tick 消失，且 `on_expire` 事件照常触发（D11-A 同 tick 兼触发碰撞事件） |
| 侧墙反弹 | `/spark particle spawn minecraft:collision_test ~ ~2 ~ 0 0 90`（rotZ=90 把初速指向 +X）朝墙抛 | 撞墙后仅水平轴反向，垂直速度保留；球沿墙面滑落而非被弹飞 |
| 半砖/楼梯 | 在台阶地形上方生成 `minecraft:collision_test` | 球停在台阶**表面**（含下半砖顶面），不穿过台阶间的空隙 |
| 薄墙穿透 | 1 格厚墙 + 高速：`… ~ ~2 ~ 0 0 90 4`（`scale=4` 把初速放大到 24 格/秒） | 不穿透（扫掠裁剪），球被挡在墙前而非穿墙 |
| 降雨落地 | 调用 `minecraft:rain`（`rain.particle.json`）于开阔地面 | 粒子在地表**消失**（`expire_on_contact=true`），中心点不进入方块 |
| 积雪堆积面 | `minecraft:snow`（无 `expire_on_contact`） | 粒子停在地表**上方**，不埋入方块；不抖动 |
| 局部空间不参与碰撞 | `/spark particle spawn minecraft:magic`（`position:true`） | 粒子**不参与碰撞**：照常穿过地形，不反弹、不因接触消失；加载期有且仅有一条告警 |
| 脚本粒子不参与碰撞 | 临时 JSON：`particle_motion_parametric` + `particle_motion_collision` | 不扫掠、不触发碰撞事件；加载期一条告警（规范里 parametric 本就只在 local space 有意义）。构建期门控已由单测 `ParticleCollisionGatingTest` 覆盖，此处只验运行表现 |
| 碰撞事件（两种格式） | 数组格式由 `minecraft:collision_test` 覆盖；对象格式把它的 `events` 改成单对象 `{"event":"collision_hit","min_speed":2}` | 两种写法都能加载；低速接触不触发、高速接触触发。解析层面的等价性已由单测覆盖 |
| 半径超限 | 把 `collision_test` 的 `collision_radius` 改为 `5` | 被夹取到 0.5（AABB 半边长 0.6），有一次性告警；球明显悬在地表上方 0.6 格——贴图半边长仍是 0.25，与碰撞盒半边长 0.6 不再重合；无卡顿、无 `getBlockCollisions` 遍历爆炸 |
| 静止不抖动 | 主用例静置 10 s | 位置不缓慢下沉、不抖动；接触期间**不重复触发**碰撞事件（D8 选 B 为上升沿触发） |
| 重叠出生（负例） | 把球生成在实心方块内部（如 `/spark particle spawn minecraft:collision_test <方块中心坐标>`），或让 `collision_radius` 中途变大 | 沿用原版内核语义：该方块**不阻挡**粒子（只裁剪前锋面之外的体素），粒子直接穿过去；不存在推出逻辑（D9 默认 A）。**这是对 Bedrock 规范的有意偏离**（规范含「移到附近非相交点」），不是 bug。注意球心距地面小于 0.25 格（中心化箱的下半）即属此例，所以生成点要在空中 |
| 第一人称排除 | 用 `FirstPersonParticleSystem` 播一个第一人称效果（如枪口粒子）——该系统当前无调用点，需临时接线（上游 M4 记为 Phase 7 未完成） | 粒子照常穿过地形/墙壁，不反弹、不因接触消失；`collisionEnabled=false` 的实例不进入扫掠（本计划明确不参与碰撞） |

调试入口沿用现有 `/spark particle spawn <effectId> …`（`ParticleCommand.kt`）；数值断言用 `/spark particle collision-stats`（§7.2）。

> 主用例自带两个需要外部配合的观察点：日志（`[collision_test] …`，INFO）与音效（`minecraft:block.stone.hit`）。默认内容包里的其它 6 个预设都没有用到 `sound_effect` 节点，若日志出现「粒子音效事件播放失败」告警，删掉 `collision_hit` 里的 `sound_effect` 节点即可，日志本身不受影响。

### 7.2 数值断言

计数器**不放静态字段**（热重载与多发射器实例都会让它失真），也**不能**「在 `CollisionResolver` 里加计数器」——它是静态工具类，`move()` 的签名里没有统计出口。落地方案：

- 在 `ParticleEmitterInstance` 上挂一个每实例的 `ParticleCollisionStats`（几个 `long` 计数 + 本 tick 计数），⑤ 的调用点**直接由返回的位标志累加** `collisionsPerTick` / `deadByContact` / `contactEdges`（零成本，`CollisionResolver` 无需感知统计）；
- `penetrationTicks` 额外需要一次 `level.getBlockState(BlockPos.containing(...))` 采样，**必须挂在 debug 开关下**（每粒子每 tick 一次方块查询，只在验证时开启）；
- `/spark particle collision-stats` 沿用现有 `ParticleCommand.kt` 的 `spawn`/`list`/`clear` 结构，聚合所有活跃发射器的实例计数后输出，并提供 `reset`。

指标口径：

- `penetrationTicks`：粒子中心点位于非空气方块内且未致死的 tick 数 → 目标 **趋近 0**（现有实现只用中心点采样且从不修正位置，rain 场景该值必然 > 0，见 §2.1 G1）。**仅统计由运动进入方块的粒子**；出生即重叠的粒子按 §4.4 会直接穿过，不计入该指标；
- `collisionsPerTick`：每 tick 碰撞次数，用于确认扫掠确实在跑；
- `deadByContact`：`expire_on_contact` 致死计数；
- `contactEdges`：接触上升沿触发的碰撞事件数 → 粒子静止期间**不应继续增长**。

### 7.3 性能

| 指标 | 目标 |
|---|---|
| 4000 并发粒子 + 碰撞启用，每 tick 总耗时 | < 2 ms（**自定目标**；`particle_soa_plan.md` M5 的口径是「8000 粒子 × 5 表达式」（`particle_soa_plan.md:1471`）且当前状态 ❌，不能当作已达标基线引用） |
| **4000 静止粒子 + 碰撞启用**（最坏场景） | 与运动态同量级 —— 静止是 snow 的常态而非例外，必须单独测 |
| 16384 粒子（`ParticleArray.MAX_CAPACITY`）+ 碰撞启用 | 无 > 5 ms 的尖峰 |
| 每 tick 小对象分配量（allocation profile） | 以 §4.4 的 6~10 次/粒子为基线，确认没有额外膨胀（每 tick new 列表、装箱等）；同时记录**预筛命中率**，作为 D1 方案 C 的输入 |
| 无碰撞组件粒子的开销 | 该类粒子不进入扫掠分支、积分公式与 `ParticleArray.integrateParticle` 逐位等价，耗时应与 `collFlags = 0` 的基线一致（差异在测量噪声内） |

> **基准必须自建预设**：`rain` / `snow` 都到不了 4000 并发。它们的稳态存活数约为 `spawn_rate × max_lifetime` —— rain `640 × 2.7 ≈ 1728`，snow `80 × 25 ≈ 2000`（`EmitterLifetimeLooping` 的 `sleep_time` 缺省 `0`，两者都是连续发射），`max_particles: 4000` 只是上限。要测 4000 并发请用专用基准预设（调高 `spawn_rate` 或拉长 `max_lifetime`），否则「4000 静止粒子」这一最坏场景永远测不到，而 D1/D8 的决策正依赖它。

需在 P1 完成后测一次，作为决策点 **D1**（是否切换方案 B/C）与 **D8**（是否升级为静止短路）的输入。

### 7.4 回归清单

- [ ] `rain` / `snow` 现有表现符合预期
- [ ] 无 `particle_motion_collision` 的粒子（如 `ParticleCommand` 的测试粒子、`space_warp` 等）不受影响
- [ ] **第一人称粒子**经实例开关跳过扫掠，行为与其它未声明 `particle_motion_collision` 的粒子一致（见 §4.7）
- [ ] `emitter_local_space` 各组合无异常：`position=true`（`magic`/`loading`）不参与碰撞、`collFlags` 恒 0、加载期各一条告警；`rotation` 为既有 no-op（§4.6 已记录，不在本计划改）
- [ ] 含 `particle_motion_parametric` 的预设不参与碰撞，且加载期一条告警
- [ ] `/reload` 后粒子定义与碰撞参数正确重建
- [ ] 双缓冲：`copyFrom()` 补齐后，粒子跨 tick 状态连续（无「碰撞参数丢失」抖动）
- [ ] 渲染 lerp 正常（被墙挡住的粒子平滑停住，而非瞬移）
- [ ] 纯函数单测：`bounce()`（`drag > |v|`、`restitution = 0`、`v = 0`、负值）与箱体尺寸（半边长 `0.1 + radius`，且 `radius` 夹取到 ≤ 0.5）
- [ ] `events` 双格式解析：数组、单对象分别得到相同结果；非法类型退化为空表且不抛异常
- [ ] 接触去重：静止粒子连续 1000 tick 只触发一次碰撞事件（D8 选 B 时）

---

## 8. 风险与对策

| 风险 | 等级 | 对策 |
|---|---|---|
| **每粒子每 tick 约 6~10 次小对象分配**（`AABB` / `Vec3` / `ImmutableList` / `BlockCollisions`，**未命中也会分配**；4000 粒子 ≈ 每 tick 数万次） | 🔴 高 | §7.3 基准测试 + allocation profile；必要时**优先切方案 C**（§5.2 空形状预筛，收益大于 B），B 作为自建扫掠的兜底 |
| **`getBlockCollisions` 逐方块遍历开销** | 🟠 中 | 仅对 `COLL_ENABLED` 粒子执行；速度上限保护（`MAX_MOVE_SQR`）；半径按规范夹取到 ≤ 0.5 且通常 ≤ 0.1 → AABB 覆盖 1~2 个方块（不夹取时一个 `collision_radius: 5` 的笔误即可让遍历爆炸） |
| **静止粒子每 tick 重复扫掠并重复触发事件**（snow 的常态：4000 粒子 × 25 s 停在地表） | 🟠 中 | 接触位去重（D8 建议 B）；性能不达标时升级为静止短路（D8-A，需接受与 vanilla `stoppedByCollision` 同源的副作用） |
| **`copyFrom` 等同步点漏改** → 碰撞参数停留在 2 tick 前的值（tick 缓冲保留自己上一轮写入的内容；且因组件每 tick 重写而难以察觉） | 🟡 低（潜在） | 4 个同步点 + `byte[]` 重载写入代码注释清单；P0 阶段断言 `collFlags` / `collRadius` 跨 tick 不丢失 |
| **幻影碰撞**：`Shapes.collide` 对 < 1e-7 的分量直接返回 0，浮点全等比较会误报碰撞 | 🟡 低 | 用 `CLIP_EPSILON` 判定（§5.2）；不要照抄 SBM 的 `!=` |
| **已重叠粒子不被阻挡**（出生在方块内 / `radius` 变大 / 方块被放到粒子身上）：该方块不阻挡它，粒子直接穿过 | 🟡 低 | 沿用原版内核语义（D9 默认 A，`VoxelShape.collide` 只裁剪前锋面之外的体素）。**这是对 Bedrock 规范的有意偏离**（规范含「moving to a nearby non-intersecting point」）；在文档与变更日志中写明，必要时另立脱困子任务 |
| **非世界空间粒子按设计不参与碰撞**（`emitter_local_space.position=true`、含 `particle_motion_parametric` 的预设） | 🟡 低 | 属有意裁定（§4.6、D10-A，依据 Bedrock 的 entity space 语义）；加载期各告警一次，并在 §4.6 给出替代做法（世界空间发射器 + `bindToAnchor`）。作者若误在局部空间预设里写 `particle_motion_collision`，表现为静默无效但有告警 |
| **行为变化**：雨滴从「冻在地表内」变为「接触即消失」；雪从「缓慢下陷」变为「停在地表上方 `0.1 + radius`」 | 🟡 低 | 属预期修复；在变更日志与 `particle_soa_plan.md` 中标注 |
| **碰撞盒偏离原版**：本计划用中心化箱，与原版/SBM 的底部锚定箱在 Y 方向差 0.1 格 | 🟡 低 | 属有意选择（D3-A），与自绘渲染的视觉中心一致；在 §3.3 与附录 B 显式记录 |
| **未加载区块**：客户端 `getBlockState` / `getBlockCollisions` 对未加载区块返回空，粒子在视距外不碰撞 | 🟡 低 | 与原版粒子一致，属可接受边界；文档显式记录，不做区块强加载 |
| **`events` 解析**：双格式都接受后，非法类型（数字/字符串等）静默退化为空表 | 🟡 低 | 加载期对「`events` 字段存在但解析结果为空」给一条 warn，避免作者以为事件已生效（G6 的加载崩溃已修） |
| **`collision_drag` 的量级口径**：规范写作「when in contact 减少 blocks/sec」，而 SBM 与计划是每个接触 tick 减一个整量（≈ 20× 速率） | 🟡 低 | 现有内容看不出差别（snow 的 `restitution` 默认 0 把 drag 乘成 0）；P1 用 `coefficient_of_restitution > 0` + 已知速度的用例标定一次，并把结论写进附录 A |
| **`expire_if_in_blocks` / `expire_if_not_in_blocks` 的可达性变化**：本计划的扫掠不允许穿透，因此 `expire_if_not_in_blocks: ["minecraft:air"]` 对参与碰撞的粒子永不触发（rain 的终止完全依赖 `expire_on_contact`） | 🟡 低 | 属预期副作用；在变更日志与 §7.1 记录：只有这类组件、没有 `expire_on_contact` 的预设会由「进方块即死」（§2.1 G4 的点采样语义）变为「贴住方块活到寿命结束」 |
| **SBM 行号与公式无法就地复核**：第 3 节、§3.4、附录 B 依赖其检出源码，而本工作区没有 SBM 源码 | 🟡 低 | 语义判断一律以 Bedrock 规范 + 原版反编译源为准，SBM 仅作行为对照；建议在附录记录其获取方式/校验和，便于后续复核 |
| **许可边界**：SBM 为 LGPL-3.0，本项目为 MIT，逐行复刻其公式/结构会形成派生 | 🟠 中 | 语义依据以 Bedrock 规范 + 原版 `Particle.move` 为准；实现与注释**不得**引用 SBM 代码或行号；第 3 节只作行为对照并固定 commit |
| **与未来物理引擎联动需求冲突**（Bullet 侧地形） | 🟡 低 | 本计划只做原版方块碰撞；物理联动另立议题 |

---

## 9. 不变部分清单

| 组件 | 说明 |
|------|------|
| `ParticleArray` SoA 布局与 `ParticleDoubleBuffer` | 仅**新增** 4 个数组，既有字段与 swap 语义不变 |
| 渲染管线（`ParticleRenderer` / BillboardHelper / RenderType / Sodium 兼容） | 仅修复 `ParticleRenderer.renderAll()` 的 light 泄漏（§11.1：启用 `particle_appearance_lighting` 的发射器不再被前一个满亮度发射器带成全亮）；其余不变，P3 只改光照采样 |
| Molang 引擎、`ParticleMolangEnvironment`、`ParticleMolangContext` | 不变；`getLevel()` / `setLevel()` 继续复用 |
| 29 个组件的反序列化与 `order()` 体系 | 仅 `ParticleMotionCollision` 的 `events` 解析与运行时行为变更 |
| `ParticleArray.integrateParticle()` / `integrate()` | **删除**：`integrate(float)` 全仓无任何调用者（死代码；仅 `ParticleDoubleBuffer.java:42` 的 javadoc 提到它，需一并更新），`integrateParticle` 的积分逻辑整体迁入 `CollisionResolver.move()`，不留第二条积分路径（见 §5.3） |
| `emitter_local_space` / `particle_motion_parametric` 的解析与存储 | 不变；本计划**不为这两类粒子创建碰撞组件**（§4.6）。既有缺陷（`rotation` 解析后无人消费、parametric 不叠加发射器变换）不在本计划范围 |
| 第一人称粒子系统（`FirstPersonParticleSystem`） | **不参与碰撞**：由实例级开关 `collisionEnabled=false` 跳过扫掠，在 ⑤ 只做无碰撞积分（其既有的 `level = null` 问题不在本计划范围，见 §4.7） |
| `SparkParticles.kt` 两个原版粒子（AnimatableShadow / SpaceWarp） | 走原版 `ParticleEngine`，不参与本计划 |
| 双端 API（`ParticleEffects` / `IParticleEffectPlayer` / Server stub） | 不变 |
| 事件系统（`EventExecutor`）框架 | 不变，仅**新增**碰撞事件的调用点与 `fireCollisionEvents(...)` |
| `particle_soa_plan.md` 已完成的 Phase 1~7 主体 | 不变 |

---

## 10. 待审阅决策点

| # | 决策点 | 备选 | 建议 |
|---|--------|------|------|
| **D1** | 扫掠实现的分配策略 | (A) 直接 `Entity.collideBoundingBox`（约 **6~10 次**小对象/粒子/tick，**含未命中**，实现最简）<br>(B) 自建：`level.getBlockCollisions` + `Shapes.collide`（省 `Vec3`/`ImmutableList`，多约 60 行；**必须照抄原版轴顺序**，见 §5.2）<br>(C) **空形状预筛 + 内核**：先用无分配预筛判断候选 1~4 个方块是否可能有碰撞形状，命中才调内核（省掉绝大多数情况的全部分配，多约 40 行） | **先用 A**；P1 基准（§7.3）测预筛命中率后，若分配压力不可接受**优先切 C**（收益大于 B），B 作为自建扫掠的兜底 |
| **D2** | 局部空间粒子与脚本粒子是否参与碰撞 | (A) 跨坐标系扫掠：把局部位移变换到世界、扫掠后再变换回局部<br>(B) **不参与碰撞**（构建期门控 + 加载期一次告警） | **B**（已裁定 2026-10-02）：依据 Bedrock 的 entity space 语义（§4.1）；(A) 本身不完整（漏掉发射器自身变换增量）又无消费方（§4.6） |
| **D3** | 碰撞盒尺寸与锚定方式 | (A) 以粒子坐标为中心、半边长 `0.1 + r`（边长 `0.2 + 2r`）<br>(B) 用 billboard 的 `width/height`<br>(C) `radius` 直接作半边长<br>(D) 原版/SBM 的箱 `[y-r, y+0.2+r]` —— **尺寸与 (A) 完全相同**，只是箱心在 `y + 0.1`（原版粒子的 `y` 是箱底，见 §3.3） | **A**：与 Spark-Core 自绘渲染的视觉中心一致（渲染把四边形以坐标为中心，`BillboardHelper.java:67-68`）；相对 (D) 等尺寸、仅锚点低 0.1 格，已在 §3.3 与附录 B 记录 |
| **D4** | Y 轴是否同样施加 `collision_drag` | (A) 三轴一致<br>(B) 照抄 SBM（Y 只乘 restitution） | **A**；SBM 的 Y 轴缺 drag 疑似照搬原版 `Particle.move` 的 X/Z-only 写法，无规范依据 |
| **D5** | `ParticleLifetimeKillPlane` / `expire_if_in_blocks` 的判定时机 | (A) 维持移动前判定（当前实现的做法，见 §2.2，与 SBM 一致）<br>(B) 移到移动后判定（更符合「粒子所在位置」字面语义） | **A**（本文档不改）；若选 B，另立子任务并评估回归。注意副作用：本计划的扫掠不允许穿透，`expire_if_*_in_blocks` 对参与碰撞的粒子不会触发（§8） |
| **D6** | 是否纳入 P3 逐粒子光照 | (A) 纳入<br>(B) 拆分为独立计划 | **B**，避免地形交互的核心修复被光照性能问题拖住 |
| **D7** | `ParticleMotionCollision` 数值字段是否保持 MoLang | (A) 保持 MoLang 表达式（Spark-Core 现有实现，见 §4.5，优于 SBM 的 `getAsFloat()` 直读）<br>(B) 退回 `getAsFloat()` 对齐 SBM | **A** |
| **D8** | 是否引入「接触状态」（静止粒子每 tick 重复扫掠 + 重复触发事件） | (A) 接触位短路扫掠：静止时视为不动（速度清零、位置不动），并抑制重复事件。**与 vanilla `Particle.move()` 的 `stoppedByCollision` 同构**（`Particle.java:189-210`），副作用同样一致：地面被移除后粒子不会重新下落，需每 N tick 强制复检<br>(B) 接触位**只用于事件去重**（上升沿触发），扫掠仍每 tick 执行<br>(C) 都不做，每 tick 全量扫掠 + 重复触发（与 SBM 一致） | **B**：先修掉可见的事件风暴，且不引入 A 的行为副作用；§7.3 基准若不达标再升级到 A（并加周期性强制复检） |
| **D9** | 已重叠粒子（出生在方块内 / `radius` 变大）是否脱困 | (A) 不处理，沿用原版 `Particle.move` 内核语义：已重叠时该方块**不阻挡**粒子、粒子直接穿过，也不会被推出<br>(B) 沿最小穿透轴推出到表面——**这才是规范要求的行为**（基岩原文含「moving to a nearby non-intersecting point for the particle (if possible)」） | **A**（本文档不改），但必须明确这是**对 Bedrock 规范的有意偏离**，而不是「沿用规范」；选 B 需定义推出速度/最大推出距离，并与原版产生可见差异 |
| **D10** | 直接写位置的组件（`ParticleMotionParametric`）如何参与碰撞 | **已裁定（2026-10-02）**：(A) 完全不参与（位置由表达式脚本拥有；规范规定 `relative_position` 相对发射器、且该组件只对 local space 有意义，扫掠它等于重犯 G5）<br>(B) 仍扫掠 `vel * tickDt`<br>(C) 检测到本 tick 有直写位置才跳过该 tick | **A**：与 §4.6 同一判据、同一处门控；顺带记录既有问题——parametric 既不要求 local space、也不叠加发射器变换（§4.6），建议另立子任务 |
| **D11** | `expire_on_contact` 致死的那一 tick，是否**同时**触发碰撞事件 | (A) 同时触发（上升沿条件与致死条件可同时成立；SBM 亦是先 `fireCollisionEvents` 再 `remove()`）<br>(B) 只触发过期事件，致死 tick 抑制碰撞事件 | **A**（与 SBM 及 Spark-Core 现有事件模型一致）；实现上注意 ⑤ 的判定顺序：先 `CONTACT_EDGE` 再 `EXPIRED`（§5.3） |

> 第一人称粒子**不参与碰撞**（§4.7，实例级开关）与局部空间/脚本粒子**不参与碰撞**（§4.6，构建期门控）均已确定，故 D2/D10 以「已裁定」记录而非待审项；第一人称不出现在上表中。

---

## 11. 实施进度

> **状态**: P0 / P1 / P2 **已实现**（改动未提交、客户端验证未做）；P3 **未开始**（D6-B 裁定拆分为独立计划）。

### 11.1 落地清单

| 阶段 | 文件 | 内容 |
|---|---|---|
| P0 | `ParticleArray.java` | 4 个碰撞数组 + `COLL_ENABLED/EXPIRE/CONTACT` 常量 + 4 个同步点 + `byte[]` 版 `growArray` 重载；删除 `integrateParticle()` / `integrate()`（位置推进只剩 `CollisionResolver.move()` 一条路径） |
| P0 | `ParticleDoubleBuffer.java` | `startTick()` 的 javadoc 改为「逻辑更新 → `CollisionResolver.move()` → compact」 |
| P1 | `CollisionResolver.java`（新增） | 速度积分 + `Entity.collideBoundingBox` 扫掠 + epsilon 裁剪判定 + 逐轴反弹（`max(0, …)` 截断）+ 位移裁剪 + 接触位 + `expire_on_contact`；半径夹取与速度上限保护都在此 |
| P1 | `ParticleComponentRuntimes.java` | 碰撞组件退化为参数写入器（读-改-写保留接触位；半径夹取 + 超限一次性告警） |
| P1 | `ParticleEmitterInstance.java` | ⑤ 位移推进阶段、`impactScratch`、`collisionEvents` 预取、`collisionEnabled` 实例开关、事件分发（先上升沿、后致死） |
| P1 | `ParticleEffectDeserializer.java` | 构建期门控 `worldSpace` + 加载期告警（「声明了碰撞组件但被门控」与「`events` 存在但解析为空」各一条） |
| P1 | `FirstPersonParticleSystem.java` | `FirstPersonEmitter` 构造时 `setCollisionEnabled(false)` |
| P2 | `ParticleMotionCollision.java` | `events` 数组/单对象双格式解析 + `CollisionEvent` 记录 + `min_speed` 缺省 2 |
| P2 | `ParticlePreset.java` | 预取 `getCollisionEvents()`，避免热路径反查组件 |
| P2 | `EventExecutor.java` | `fireCollisionEvents(List<CollisionEvent>, int, ParticleArray, float)` |
| §7.2 | `ParticleCollisionStats.java`（新增）、`ParticleCommand.kt` | 每实例计数（全部由返回位标志累加）+ `/spark particle collision-stats [reset\|penetration]` |
| §4.8 | `ParticleRenderer.java` | 修复该节记录的 `renderAll()` light 泄漏（就地改写入参 → 逐发射器独立变量） |
| §7.1 | `collision_test.particle.json`（新增内容） | 基于 `snow` 改出的方块碰撞测试粒子（`minecraft:collision_test`）：单球、垂直初速 6、重力 9.8、`radius 0.15` / `drag 1.5` / `restitution 0.6` / 不因接触消失，带两级 `min_speed`（2 / 8）的**数组格式**碰撞事件（log + sound）；视觉半边长与碰撞盒半边长相等，用于肉眼判断「是否陷入方块」 |
| 顺带（碰撞之外） | `EmitterShapeAdapter.java` + 四个形状类、`ParticleAppearanceBillboard.java`、`ParticleComponentRuntimes.java` | 统一「内容字段名/格式与反序列化」的口径：`facing_camera_mode`（规范名）与 `face_camera_mode` 两种写法都接受；形状组件的 `direction` 同时接受三元素向量与 `"inwards"/"outwards"` 字符串；`uv`/`uv_size` 的非数值元素回退默认值 + 告警一次（目的是不让单个字段拖垮整个定义）；翻页书接受规范键名 `base_UV`/`size_UV`/`step_UV`，并按像素→归一化 UV 与 `stretch_to_lifetime` 帧号换算修正运行时 |
| 测试 | `CollisionResolverTest.java`、`ParticleCollisionGatingTest.java`、`CollisionTestParticleContentTest.java`、`ContentParticleLoadTest.java`、`ParticleComponentFieldTest.java`、`FlipbookUvTest.java`（新增） | 43 项：`bounce()` 4 / 箱体与半径夹取 2 / `events` 解析 5 / 无碰撞快路径逐位等价 2 / 构建期门控端到端 5 / 测试粒子内容校验 4 / 内容包整体加载 2 / 组件字段名与格式 14 / 翻页书 UV 与帧号 6 |

### 11.2 实现相对本文档的偏差

| # | 偏差 | 理由 |
|---|---|---|
| 1 | `move()` 增加**零位移短路**（待位移 `fx/fy/fz` 全零时不扫掠） | 与 vanilla `Particle.move` 的 `x!=0\|\|y!=0\|\|z!=0` 守卫同源；否则完全静止的粒子每 tick 白付一次扫掠分配 |
| 2 | `halfExtent()` 把半径**双端夹取**到 `[0, 0.5]`（§4.4 只写了上界） | 负值与 NaN 会把碰撞盒缩到基础尺寸以下；NaN 用 `!(r > 0f)` 一并归零 |
| 3 | 半径超限告警落在**组件**（每预设一次），`CollisionResolver` 只夹取、不告警 | 解析器是静态工具类，拿不到 effectId；组件侧可以带上原始表达式字符串 |
| 4 | `collFlags == 0` 时跳过 `setCollFlags` | 未声明碰撞组件的粒子连一次字节写都不产生（§4.3「零额外开销」的字面落实） |
| 5 | `COLL_*` 常量声明在 `ParticleArray` 顶部常量区（§5.1 的片段把它们与数组排在同一代码块） | 与 `MAX_CAPACITY` 等既有常量同处，仅为布局差异，不影响语义 |
| 6 | `ParticleMotionCollision` 新增 `isEventsFieldPresent()` | §8「`events` 存在但解析结果为空」的加载期告警需要它区分「没写」与「写了但无效」 |
| 7 | `penetrationTicks` 的采样开关是 `ParticleCollisionStats` 的静态 volatile 字段，由指令切换 | §7.2 只要求「挂在 debug 开关下」；计数器本身仍是每实例、非静态 |
| 8 | `CollisionEvent` 实现为 `ParticleMotionCollision` 的嵌套 record | 该组件的 schema 集中在一个文件里；§5.4 未规定它的归属 |

### 11.3 验证结果与未覆盖面

- `compileJava` / `compileKotlin` / `test` 全部通过；新增 43 项单测全绿。其中逐位等价测试用 `Float.floatToIntBits` 逐字段比对，把 P0 的验收前提变成了可回归断言；门控测试真正跑了 `ParticleEffectDeserializer` + Molang 编译，覆盖世界空间/局部空间/parametric 三条分支；内容级测试直接加载 §7.1 的测试粒子文件，校验碰撞组件确实被创建、字段与事件节点与设计一致；`ContentParticleLoadTest` 逐个加载内容包里的全部预设并断言「允许加载失败的集合为空」，`rain`/`snow` 的 UV 表达式回退落地后该集合已清空；`FlipbookUvTest` 用 `magic` 的真实参数钉住翻页书的像素→归一化换算与 `stretch_to_lifetime` 帧号。
- **未覆盖（需要客户端会话）**：扫掠本身无法单测——`Entity.collideBoundingBox` 需要真实 `Level`。§7.1 的 12 个手动场景、§7.3 的 4000 并发/静止基准与分配剖析、附录 A 的 `collision_drag` 量级标定**均未做**，因此 **D1（是否切空形状预筛 C）与 D8（是否升级静止短路 A）的决策输入仍然缺**；D9 与 D11 已按建议方案实现，D5 维持本文档不变。

---

## 附录 A：单位与公式推导

| 量 | Spark-Core | SBM | 换算 |
|---|---|---|---|
| `tickDt` | `0.05f` 固定（`ParticleVisualEffectRenderer.java:34`） | `1/20f` | 相同 |
| 粒子速度 | **方块/秒**（`integrateParticle` 用 `pos += vel * tickDt`） | **方块/秒**（`particleData.vx`），`move()` 内 `/20f` 转方块/tick | 相同 |
| `collision_drag` | 本计划：方块/秒 | `move()` 中 `/20f`（每 tick 减免量） | **`\|v\|_s - drag` 即等价于 SBM 的 `\|v\|_tick - drag/20`** |
| `coefficient_of_restitution` | 无量纲乘数 | 同 | 同 |
| `collision_radius` | 方块（半边长加数） | 同 | 同 |

> **移植陷阱**：SBM 的 `(Math.abs(xd) - collisionDrag / 20f)` 里 `xd` 是**方块/tick**，`/20f` 是单位换算而非缩放系数。Spark-Core 速度为方块/秒，因此**直接减 `collisionDrag`**，不要再除以 20。

碰撞响应（本计划，v 与 drag 均为方块/秒）：

```
被挡住的轴：v' = -sign(v) * max(0, |v| - drag) * restitution
未挡住的轴：v' = v
位置：      pos' = pos + clippedDelta      （clippedDelta 为逐轴裁剪后的位移）
```

> **四条口径补充**：
> - **碰撞事件的速度**取**反弹前**的 `|v|`（方块/秒），与 SBM 一致（SBM 在回写 `particleData.vx/vy/vz` 之前计算 speed，`SnowStormParticle.java:230-235`）；
> - **速度上限 `10000`** 作用在**方块/tick 的位移平方**上，与原版 `Particle.MAXIMUM_COLLISION_VELOCITY_SQUARED = Mth.square(100.0)`（`Particle.java:22,196`）口径相同 —— 不要按方块/秒去比较；
> - **`min_speed` 的单位是方块/秒**，由规范确认（原文 "default/minimum is **2 blocks/sec**"），与上表「粒子速度 = 方块/秒」一致；
> - **`collision_drag` 的量级待标定**：规范写作「slows down the particle by this amount in blocks/sec **when in contact**」，读作速率（blocks/s²）；本表由 SBM 推出的口径则是**每个接触 tick 减一个整量**（≈ 20× 速率）。snow 的 `restitution` 默认 0 会把 drag 乘成 0，现有内容看不出差别；P1 用 `coefficient_of_restitution > 0` 的用例标定一次并回填本表。

---

## 附录 B：与 SBM 的差异对照

| 项 | SBM | 本计划 | 理由 |
|---|---|---|---|
| 逐轴 drag | 仅 X/Z 减 drag，Y 只乘 restitution | 三轴一致 | SBM 疑似照搬原版 `Particle.move` 的 X/Z-only 写法 |
| `drag > \|v\|` | 符号翻转 → 反向弹射 | `max(0, …)` 截断 | 避免小速度下抖动/倒飞 |
| 碰撞盒 | 原版 0.2 立方**底部锚定** + `inflate(radius)`：`[y-r, y+0.2+r]`，半边长 `0.1 + r`、箱心在 `y + 0.1` | **同尺寸**的箱，箱心改为粒子坐标 `y` | 与 Spark-Core 自绘渲染的视觉中心一致；相对 SBM 整体低 0.1 格（等尺寸，仅锚点不同，见 D3） |
| 裁剪判定 | 浮点全等（`x != collided.x`） | `\|clipped - delta\| > 1e-7` | 原版 `Shapes.collide` 会把 < 1e-7 的分量归零，全等比较会误报碰撞 |
| 速度上限 | `velSqr < 10000` 才扫掠 | 同 | 与原版 `Particle` 同源；防止 `expandTowards` 产生超大 AABB |
| 分配 | 调用侧 1 `AABB` + 1 `Vec3` | 相同（D1：可切方案 B 或 C 优化） | 两侧实际都还有 `ImmutableList` / `BlockCollisions` 的分配（§4.4）；C 的空形状预筛能把常见情况（空气）的分配降到接近 0 |
| 接触状态 | 只记 `onGround`，不短路扫掠；接触期间每 tick 重复触发事件 | 接触位 + 上升沿去重（D8） | 静止是 snow 的常态，避免重复事件与无谓扫描 |
| 已重叠粒子 | 不脱困 | 同（D9 默认 A） | 沿用原版内核语义；注意这是**对 Bedrock 规范的有意偏离**（规范含「移到附近非相交点」），不是规范行为 |
| 事件速度口径 | 反弹前 `\|v\|` | 同 | 语义对齐 |
| 数值字段 MoLang | 不支持（`getAsFloat()`） | 保留全字段 MoLang | 更贴基岩版规范 |
| `expire_on_contact` | 只 `remove()`，不通知 emitter | 标记死亡 + 触发过期事件 | 保持 Spark-Core 事件模型一致 |
| 局部空间 | 投递时一次性转世界坐标（粒子此后不再跟随发射器），再在**世界坐标**里正常碰撞 | **不参与碰撞**，粒子继续跟随发射器 | `position=true` 在规范里是 entity space（§4.1），没有可碰撞的地形；SBM 的做法实际是拿「丢掉 local space 语义」换碰撞，本计划选择保留语义（§4.6） |
| 第一人称粒子 | SBM 无第一人称粒子系统 | **不参与碰撞**（实例级开关） | 产品决定：视图层装饰不与世界交互（§4.7） |

---

## 附录 C：工作量估算

| Phase | 文件数 | 行数（预估） | 依赖 |
|-------|--------|-------------|------|
| P0 基础设施 | 4 | ~150 | 无 |
| P1 扫掠碰撞（核心 + 两道门控） | 5 | ~350 | P0 |
| P2 事件与解析 | 4 | ~120 | P1 |
| P3 逐粒子光照（可选） | 2 | ~140 | P1 |
| **合计** | **~7**（新增 1） | **~760** | |

> 估算口径：含注释与白盒单测。P1 的 5 个文件见 §6（`CollisionResolver` / `ParticleComponentRuntimes` / `ParticleEmitterInstance` / `ParticleEffectDeserializer` / `FirstPersonParticleSystem`）；P2 的 4 个文件见 §6（`ParticleMotionCollision` / `ParticleEffectDeserializer` / `EventExecutor` / `ParticleEmitterInstance`）。

### 上游文档同步（已完成）

- `particle_soa_plan.md` 已新增阻塞项 **B12**（粒子方块碰撞为打点采样实现，状态标记 ✅ 已实现）与 **B13/B14**（`particle_motion_parametric` 未按规范实现、`emitter_local_space.rotation` 解析后无人消费且 `rotation && !position` 非法），Phase 4 进度表补记碰撞实现与遗留验证；
- 该文档中描述点采样实现的几处已改写：§5.1 线程表的「碰撞检测 … `Level.getBlockState()` 仅主线程」、§5.2 tick 伪代码里的 `collisionHandler.check(i, buf, level)`、架构图/数据流/时序图三处 tick 步骤；风险表的「碰撞检测开销」已按本计划 §7.3 改写；
- 该文档的 M5 已补注：该里程碑从未实测，不得当作已达标基线；碰撞的独立基准口径指向本计划 §7.3。

### 实现约束（长期有效）

- 实现与注释**不得**引用 SBM 代码或行号（§8 许可边界）；第 3 节的对照行号只存在于本文档。

---

## 修订记录

- **2026-09-30**：初稿（设计评审版）。
- **2026-10-01**：对照 Spark-Core `13c40cd`、SBM `1.21.1-dev@c314b1e` 与 NeoForge `21.1.219` 反编译源核对后修订。要点：更正碰撞内核的轴顺序（先裁位移较大的水平轴）与碰撞盒锚定描述（原版箱为底部锚定，本计划改用中心化箱）；明确**第一人称粒子不参与碰撞**；`CollisionResolver` 改为位标志返回 + epsilon 裁剪判定 + 接触位事件去重；补充 `copyFrom` 漏改的失效语义、`byte[]` 版 `growArray` 重载、`events` 仅数组格式的解析规则、已重叠粒子直接穿过的语义；新增决策点 **D8–D10**；工作量估算由 ~680 行调整为 ~810 行。
- **2026-10-02**：二审核对（新增 Microsoft Learn 组件文档为依据）后修订。要点：新增**世界空间判据**（§4.1）——局部空间粒子（`emitter_local_space.position=true`）与脚本粒子（`particle_motion_parametric`）裁定**不参与碰撞**，初审稿 §4.6 的跨坐标系扫掠方案废弃，改为构建期门控 + 加载期一次告警（D2/D10 记为「已裁定」）；第一人称改为**实例级开关**并改写理由（原「`level` 为 null」的因果不成立）；修正 §4.4/§5.2 的原版轴顺序描述（**先裁的水平轴会移动盒子、后裁的不移动**，原描述相反）与 §3.3/D3 的碰撞盒描述（与 SBM **等尺寸**，仅箱位低 0.1，原文「半边长只在 X/Z 成立」有误）；`collision_radius` 按规范夹取 ≤ 0.5；`events` 改为数组/对象**双格式**（规范二者都合法）；P0 验收改为以 float 快路径保证逐位一致；§7.2 统计改为实例级累加（静态类无法承载计数）；§7.3 更正 M5 引用（8000 粒子、当前 ❌）并指出 rain/snow 达不到 4000 并发、基准须自建预设；补充 `collision_drag` 量级标定、`expire_if_*_in_blocks` 可达性变化、SBM 不可就地复核等风险；新增 **D11**；工作量 ~810 → ~760。
- **2026-10-02（实现）**：P0–P2 落地并同步上游文档。要点：`ParticleArray` 新增 4 个碰撞数组 + 4 个同步点 + `byte[]` 版 `growArray` 重载，删除 `integrateParticle()` / `integrate()`；新增 `CollisionResolver`（`Entity.collideBoundingBox` 扫掠 + 逐轴反弹 + 位移裁剪 + 接触位 + `expire_on_contact`）；碰撞组件退化为参数写入器（读-改-写保留接触位）；`ParticleEmitterInstance` 的每粒子流程改为「组件 tick → 时间线事件 → 位移推进阶段 → 事件分发」；构建期 `worldSpace` 门控 + 加载期告警；`events` 双格式解析 + `EventExecutor.fireCollisionEvents`；`FirstPersonEmitter` 实例级开关；顺带修复 §4.8 记录的 `renderAll()` light 泄漏。新增 **§11 实施进度**（落地清单、8 处实现偏差、验证结果与未覆盖面），附录 C 的「建议同步更新」改写为已完成的同步记录 + 长期约束。验证：`compileJava`/`compileKotlin`/`test` 通过，新增 17 项单测；扫掠本身、§7.3 基准与 `collision_drag` 标定仍待客户端会话。
- **2026-10-02（测试内容）**：新增默认内容包测试粒子 `minecraft:collision_test`（`collision_test.particle.json`，基于 `snow` 改出），§7.1 改为以它为主用例并给出可直接执行的命令、id 口径说明与各场景的具体操作；新增内容级单测 `CollisionTestParticleContentTest`（4 项）把该文件的设计约定钉成断言。单测总数 17 → 21；§7.1 原先写作 `snowstorm:*` 的命令键更正为注册键 `minecraft:*`。
- **2026-10-02（组件字段）**：新增 `ContentParticleLoadTest`（逐个加载内容包里的全部 `.particle.json`）后发现**三个预设整体无法加载**，均为「内容字段名/格式与反序列化不一致」，全部已修：`facing_camera_mode`（规范字段名，此前只有拼写错误的 `face_camera_mode` 被读取 → 7 个预设的朝向模式全部静默落入默认值）、形状组件的 `direction` 字符串形式 `"inwards"/"outwards"`（此前 `getAsJsonArray()` 在字符串上抛 `ClassCastException` → `minecraft:magic` 整体缺失）、`rain`/`snow` 的 `uv.uv` MoLang 表达式（`getAsFloat()` 抛 `NumberFormatException` → 两者整体缺失）。修复方式：`ParticleAppearanceBillboard` 同时接受两种拼写、`uv`/`uv_size` 的非数值元素回退默认值 + 告警一次；`EmitterShapeAdapter` 新增 `readVec3` / `readDirection` / `readDirectionInwards` 三个安全读取辅助，四个形状类统一改用（非数组值一律回退默认值，不让单个字段拖垮整个定义）。顺带修好翻页书：接受规范键名 `base_UV`/`size_UV`/`step_UV`，并修正运行时两处——UV 三元组是像素坐标需按纹理尺寸归一化、`stretch_to_lifetime` 的帧号由「0..1 进度直接取整」改为「进度 × 最大帧数」（后者此前永远停在第一帧）。上游 B11/§14.5 同步改为「翻页书已可用，剩 `uv`/`uv_size` 求值与 `uv_mode`」。新增 `ParticleComponentFieldTest`（14 项）与 `FlipbookUvTest`（6 项，用 `magic` 的真实参数钉住像素→归一化换算与 `stretch_to_lifetime` 帧号）。单测总数 21 → 43（`ContentParticleLoadTest` 的「允许加载失败集合」已清空）。
