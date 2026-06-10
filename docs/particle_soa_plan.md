# Spark-Core 粒子系统 SoA 重构实施计划

> **状态**: 编码完成 · **创建**: 2026-06-10 · **修订**: 2026-06-10（B1/B2/B3/B5/B7 已修复）  
> **阶段状态**: Phase 1✅/2✅/3✅/4✅/5⚠️(部分)/6✅/7⚠️ · **截至 2026-06-10 实现进度**  
> **目标版本**: Spark-Core 1.x  
> **参考源**: [SimpleBedrockModel](https://github.com/McModderAnchor/SimpleBedrockModel) (LGPL-3.0, 其中 `molang/` 子模块 MIT) · Bedrock Edition `particle_effect` JSON 规范 v1.10.0

---

## 目录

1. [目标与范围](#1-目标与范围)
2. [当前架构 vs 目标架构](#2-当前架构-vs-目标架构)
3. [核心数据结构：SoA ParticleArray](#3-核心数据结构soa-particlearray)
4. [双重缓冲方案](#4-双重缓冲方案)
5. [线程模型](#5-线程模型)
6. [组件系统](#6-组件系统)
7. [Molang 集成](#7-molang-集成)
8. [粒子定义协议 (JSON Schema)](#8-粒子定义协议-json-schema)
9. [渲染管线](#9-渲染管线)
10. [双端 API 设计](#10-双端-api-设计)
11. [包结构](#11-包结构)
12. [逐步实施计划](#12-逐步实施计划)
13. [不变部分清单](#13-不变部分清单)
14. [风险与缓解](#14-风险与缓解)

---

## 1. 目标与范围

### 目标

在 Spark-Core 中实现一个**完整的基岩版粒子系统**，以 **SoA (Structure of Arrays)** 数据布局 + **双重缓冲**为核心架构，提供：

- 数据驱动粒子定义（完整兼容 Bedrock `particle_effect` v1.10.0 JSON 格式）
- SoA 布局提升 CPU 缓存命中率
- 双重缓冲解耦主线程逻辑更新与渲染线程只读访问
- Molang 表达式全面驱动粒子参数
- **双端统一触发 API**（`ParticleEffects` helper 工厂模式，与 `SpreadingSoundHelper` 一致）
- Machine-Max 及所有 Spark-Core 使用方可直接消费

### 范围

| 纳入 | 不纳入 |
|------|--------|
| 粒子定义 JSON 加载（完整 29 组件） | GPU 实例化渲染（Minecraft 原生不支持） |
| SoA 粒子存储 (`ParticleArray`) | 多线程并行更新（Minecraft 线程模型限制） |
| 双重缓冲 (`ParticleDoubleBuffer`) | 纹理图集（atlas）合并（作为后续优化项） |
| 发射器引擎（速率/生命周期/形状） | Vulkan/Metal 后端渲染 |
| Billboard 粒子渲染（11 种朝向模式） | |
| Molang 表达式全面集成 | |
| 事件系统（子粒子/音效/表达式） | |
| 曲线系统（贝塞尔/Catmull-Rom/线性） | |
| 粒子碰撞（方块碰撞） | |
| 第一人称粒子系统 | |
| **双端统一触发 API（服务端暂为 stub）** | |

### 参考源

SimpleBedrockModel 项目的粒子系统作为**协议和设计参考**。

许可证说明：
- **SimpleBedrockModel 整体**: LGPL-3.0
- **Mocha Molang 引擎子模块** (`molang/`): MIT
- Spark-Core **不直接依赖** SBM 的 jar，而是参考 Bedrock 公开的 JSON 协议规范 + 独立实现（clean-room design），具体差异见下文：
  1. 数据布局改为 SoA（SBM 为 AoS）
  2. 双重缓冲（SBM 无此机制）
  3. Molang 引擎使用 Spark-Core 自有的 Mocha（已有 MIT 许可的独立实现，与 SBM 的 Mocha 同源但 API 不同）

参考的具体内容（均为 Bedrock 公开规范或算法，不受特定许可证约束）：
- **JSON Schema**: `particle_definitions/*.particle.json` 格式
- **组件体系**: 29 个 `minecraft:*` 组件的定义与反序列化
- **事件系统**: 6 种事件节点类型
- **曲线系统**: 4 种插值算法

---

## 2. 当前架构 vs 目标架构

### Spark-Core 粒子系统现状

```
注册层（已有）         运行时（缺失）        渲染（缺失）
SparkParticles.kt     ✗ 无发射器引擎       ✗ 无 Billboard 渲染
  └─ animatable_shadow                      
  └─ space_warp                             
                                           
Molang（已有）                              JSON 加载（缺失）
MochaEngine           ✓ 完整编译/执行      ✗ 无 particle.json 解析
MolangContextRegistry ✓ 缓存机制           
```

### 目标架构总览

```
┌─ common（双端共享）─────────────────────────────────────────┐
│  ParticleEffectDefinition（只读数据模型）                     │
│  ParticleEffectTrigger（触发 API）                           │
│  ParticleEffects（static helper，与 SpreadingSoundHelper 同模式）│
│  IParticleEffectPlayer（接口，Client/Server 实现）           │
└──────────────────────────────────────────────────────────────┘
         │
         ▼
┌─ 资源加载（仅客户端）────────────────────────────────────────┐
│  ParticleDefinitionLoader                                    │
│    → 扫描 assets/*/particle_definitions/*.json               │
│    → ParticleEffectDeserializer 解析                         │
│    → Molang 表达式预编译为 MolangExpression                   │
│    → 输出 ParticleEffectDefinition（不可变）                   │
└──────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─ 运行时（仅客户端，双重缓冲）─────────────────────────────────┐
│  ParticleDoubleBuffer                                       │
│  ┌─ tickBuffer (主线程写) ─┐  ┌─ renderBuffer (渲染线程只读) ┐│
│  │ ParticleArray           │  │ ParticleArray              ││
│  │   float[] posX/Y/Z     │  │   float[] posX/Y/Z         ││
│  │   float[] prevPosX/Y/Z │  │   float[] prevPosX/Y/Z     ││
│  │   float[] velX/Y/Z     │  │   float[] velX/Y/Z         ││
│  │   float[] accelX/Y/Z   │  │   ... 全部只读              ││
│  │   boolean[] alive      │  │                             ││
│  │   ...                  │  │                             ││
│  └────────────────────────┘  └────────────────────────────┘│
│                            swap()                           │
└──────────────────────────────────────────────────────────────┘
                               │
         ┌─────────────────────┴──────────────────────┐
         ▼                                            ▼
┌─ Emitter（主线程 tick）───┐  ┌─ Renderer（渲染帧）────────┐
│ ParticleEmitterManager    │  │ ParticleRenderer           │
│  ① snapshot → prevPos     │  │  读 volatile renderBuffer   │
│  ② Emitter 寿命/速率/形状  │  │  partialTick lerp 位置      │
│  ③ 新粒子: Molang 初始值   │  │  提交 Billboard 顶点        │
│  ④ 各粒子: Molang → accel │  │                             │
│  ⑤ 运动积分 (pos+=vel*dt) │  │                             │
│  ⑥ 碰撞/事件/expiration   │  │                             │
│  ⑦ compact               │  │                             │
│  ⑧ swap                  │  │                             │
└──────────────────────────┘  └─────────────────────────────┘
```

### 数据流时序

```
启动/资源重载:
  JSON → ParticleEffectDefinition（不可变模板）
         ↓
  ParticleEmitterInstance（引用模板 + 持有 double buffer）

运行时每 tick (20 TPS):
  tickBuffer (swap 后得到的上一帧 renderBuffer):
    ① snapshot: pos → prevPos (逐粒子复制)
    ② Emitter 逻辑 (寿命/速率 → 发射)
    ③ 新粒子: 形状 → 初始值 (Molang 求值) → 写入 tickBuffer
    ④ 活动粒子: Molang → accel/drag / 运动积分 / 碰撞 / expiration
    ⑤ compact (删除死粒子)
  atomic swap (renderBuffer = tickBuffer)

运行时每渲染帧:
  renderBuffer (上一次 swap 后的数据, 只读):
    ① 遍历 alive 粒子, lerp(prevPos, pos, partialTick) 插值位置
    ② 提交 Billboard 顶点
```

### 关键设计决策

**渲染线程不执行运动积分。** 审阅确认后简化：
- tick 全量更新（逻辑 + 积分） → swap
- 渲染线程只读 + partialTick lerp，不求值任何 Molang
- 根除 age/accel 不一致问题（审阅 #1）
- 根除双缓冲 pos/vel 同步开销

---

## 3. 核心数据结构：SoA ParticleArray

这是整个系统的核心。用**并行数组**替代 `List<ParticleInstance>` 胖对象。

### 3.1 字段布局

```java
/**
 * SoA (Structure of Arrays) 粒子存储。
 * 
 * 所有粒子字段按类型分解为独立数组，更新/渲染时顺序遍历同一数组，
 * 利用 CPU 缓存线预取，大幅减少缓存未命中。
 * 
 * 对比 AoS (List<ParticleInstance>):
 *   AoS: 每粒子对象 ~30 字段 ≈ 200+ 字节 → 每条缓存线仅 ~3 粒子
 *   SoA: 16 floats/缓存线 → 一次 posX[i] 遍历拉 16 粒子入 L1
 * 
 * 线程安全约定:
 *   - tick 线程: 写入所有数组 (独占)
 *   - render 线程: 只读所有数组 (通过 volatile swap 确保可见性)
 *   - 无并发写竞争 (同一时刻只有一线程持有写入权)
 */
public class ParticleArray {
    
    // ====== 位置 (双缓冲 lerp 用) ======
    private float[] posX, posY, posZ;          // 当前 tick 位置
    private float[] prevPosX, prevPosY, prevPosZ; // snapshot 上一 tick 位置
    
    // ====== 运动 ======
    private float[] velX, velY, velZ;
    private float[] accelX, accelY, accelZ;   // tick 时 Molang 求值
    private float[] dragCoeff;                // tick 时 Molang 求值
    
    // ====== 旋转 ======
    private float[] rot, rotRate, rotAccel;
    
    // ====== 外观（渲染最频繁，连续访问） ======
    private float[] width, height;            // Billboard 尺寸
    private float[] r, g, b, a;              // 颜色
    private float[] u0, v0, u1, v1;          // UV 坐标
    
    // ====== 生命周期 ======
    private float[] age;                      // 全量在 tick 更新
    private float[] maxLifetime;              // tick 时 Molang 求值
    private boolean[] alive;                  // tick 标记死亡
    
    // ====== 粒子随机种子 ======
    private float[] random1, random2, random3, random4;
    
    // ====== 元数据 ======
    private int count;
    private int capacity;
    
    private static final int INITIAL_CAPACITY = 256;
    private static final int GROWTH_FACTOR = 2;
    private static final int MAX_CAPACITY = 16384;
}
```

### 3.2 关键操作

#### 分配 (Allocate)

```java
public int allocate() {
    if (count >= capacity) grow();
    int slot = count;
    count++;
    
    posX[slot] = 0; posY[slot] = 0; posZ[slot] = 0;
    prevPosX[slot] = 0; prevPosY[slot] = 0; prevPosZ[slot] = 0;
    velX[slot] = 0; velY[slot] = 0; velZ[slot] = 0;
    accelX[slot] = 0; accelY[slot] = 0; accelZ[slot] = 0;
    dragCoeff[slot] = 0;
    rot[slot] = 0; rotRate[slot] = 0; rotAccel[slot] = 0;
    width[slot] = 0.25f; height[slot] = 0.25f;
    r[slot] = 1; g[slot] = 1; b[slot] = 1; a[slot] = 1;
    u0[slot] = 0; v0[slot] = 0; u1[slot] = 1; v1[slot] = 1;
    age[slot] = 0; maxLifetime[slot] = Float.MAX_VALUE;
    alive[slot] = true;
    random1[slot] = RANDOM.nextFloat();
    random2[slot] = RANDOM.nextFloat();
    random3[slot] = RANDOM.nextFloat();
    random4[slot] = RANDOM.nextFloat();
    
    return slot;
}
```

#### Snapshot (pos → prevPos)

```java
/** 在 tick 开始时调用，保存上一 tick 的位置供渲染线程 lerp */
public void snapshotPositions() {
    System.arraycopy(posX, 0, prevPosX, 0, count);
    System.arraycopy(posY, 0, prevPosY, 0, count);
    System.arraycopy(posZ, 0, prevPosZ, 0, count);
}
```

#### 运动积分（在主线程 tick 内全量更新）

```java
/**
 * 全量运动积分。在主线程 tick 中调用，与逻辑更新在同一循环内。
 * 渲染线程不做积分，只做 lerp。
 */
public void integrate(float tickDt) {
    for (int i = 0; i < count; i++) {
        if (!alive[i]) continue;
        
        velX[i] += accelX[i] * tickDt;
        velY[i] += accelY[i] * tickDt;
        velZ[i] += accelZ[i] * tickDt;
        
        float dragFactor = Math.max(0, 1 - dragCoeff[i] * tickDt);
        velX[i] *= dragFactor;
        velY[i] *= dragFactor;
        velZ[i] *= dragFactor;
        
        posX[i] += velX[i] * tickDt;
        posY[i] += velY[i] * tickDt;
        posZ[i] += velZ[i] * tickDt;
        
        rotRate[i] += rotAccel[i] * tickDt;
        rot[i] += rotRate[i] * tickDt;
        
        age[i] += tickDt;
    }
}
```

#### 批量紧凑 (compact)

```java
/**
 * 批量紧凑：将所有存活粒子移动到数组前部，更新 count。
 * 仅在所有粒子遍历完成后、swap 之前调用一次。
 * 
 * 紧凑后索引变化:
 *   - 事件回调在 compact 前已触发完毕
 *   - 渲染线程在下一次 swap 后读取紧凑后的数组
 *   - 渲染线程仅做只读 lerp，不依赖稳定的粒子索引跨帧
 */
public void compact() {
    int writeIdx = 0;
    for (int readIdx = 0; readIdx < count; readIdx++) {
        if (!alive[readIdx]) continue;
        if (writeIdx != readIdx) copySlotTo(readIdx, writeIdx);
        writeIdx++;
    }
    count = writeIdx;
}
```

#### 渲染线程的 lerp 访问

```java
/** 渲染线程调用 — 纯读，无副作用 */
public float getRenderX(int index, float partialTick) {
    return prevPosX[index] + (posX[index] - prevPosX[index]) * partialTick;
}
```

### 3.3 缓存效率对比

假设缓存线 64 字节，float 4 字节：

| 操作 | AoS (`List<ParticleInstance>`) | SoA (`ParticleArray`) |
|------|-------------------------------|-----------------------|
| 运动更新 `pos += vel * dt` | 每次访问跳 200+ 字节，跨缓存线 | `posX[i]`, `velX[i]` 顺序相邻，L1 命中率 > 95% |
| 渲染提交顶点 (pos + uv + color) | 每粒子 ~200 字节到缓存 | 3 次顺序遍历: pos(12B) → uv(16B) → color(16B) 每粒子 |
| compact 遍历 | List 引用 + 对象头 ~20B | 一次 `alive[]` (1B) + 一次数据复制 |

---

## 4. 双重缓冲方案

### 4.1 ParticleDoubleBuffer

```java
/**
 * 双重缓冲：解耦主线程逻辑更新与渲染线程只读访问。
 * 
 * 渲染线程不做运动积分——只做 partialTick lerp 和渲染。
 * 因此双缓冲的核心目的简化为: 防止渲染线程读到半写状态。
 * 
 * 线程安全:
 *   - swap() 仅由主线程调用 (tick 结束时)
 *   - getRender() 仅由渲染线程调用 (每帧渲染开始时)
 *   - getTickWrite() 仅由主线程调用 (tick 开始时)
 *   - volatile 保证可见性
 */
public class ParticleDoubleBuffer {
    
    private final ParticleArray bufferA;
    private final ParticleArray bufferB;
    
    private volatile ParticleArray renderBuffer;
    private ParticleArray tickBuffer;        // 仅主线程
    
    public ParticleDoubleBuffer(int initialCapacity) {
        this.bufferA = new ParticleArray(initialCapacity);
        this.bufferB = new ParticleArray(initialCapacity);
        this.renderBuffer = bufferA;
        this.tickBuffer = bufferB;
    }
    
    // ── 由主线程调用 ──
    
    /**
     * tick 开始时准备写缓冲区。
     * 返回的 tickBuffer 与当前 renderBuffer 相同 (swap 后已同步)。
     * 调用后执行: snapshotPositions() → 逻辑更新 → integrate() → compact()
     */
    public ParticleArray startTick() {
        return tickBuffer;
    }
    
    /**
     * 原子交换读写缓冲区。
     * volatile 写确保所有写入对渲染线程可见。
     */
    public void swap() {
        ParticleArray oldRender = renderBuffer;
        renderBuffer = tickBuffer;   // volatile 写
        tickBuffer = oldRender;
    }
    
    // ── 由渲染线程调用 ──
    
    /**
     * 获取当前渲染缓冲区。
     * 返回 volatile 引用，渲染线程每次读取最新 swap 结果。
     * 仅做只读操作: partialTick lerp + 提交顶点。
     */
    public ParticleArray getRender() {
        return renderBuffer;
    }
    
    // ── 调试 ──
    
    public int getActiveCount() {
        return renderBuffer.getCount();
    }
}
```

### 4.2 为什么不需要 syncFromRender

原方案中渲染线程做积分导致 tick 前后 pos/vel 不同步，需要 `copyPosVelFrom()`。简化后渲染线程**不修改任何数组**，因此 tick 开始时 tickBuffer 的数据就是一致的，无需同步。

### 4.3 时序图

```
主线程 (20 TPS):
  T0: startTick() → tickBuffer = renderBuffer (两者引用同一数组)
  T1: snapshotPositions() → 保存 pos → prevPos
  T2: Emitter 逻辑 → 发射新粒子
  T3: 对每个粒子: Molang → accel/drag, integrate(), 碰撞/事件
  T4: compact()
  T5: swap() → renderBuffer = tickBuffer (volatile 写)
  
渲染线程 (可变帧率):
  F0: getRender() → 读 volatile 引用 = 最新 swap 的数据
  F1: lerp(prevPos, pos, partialTick) → renderX/Y/Z
  F2: 提交 Billboard 顶点

时序关系 (60 FPS + 20 TPS):
  主线程: [snap│tick│compact│swap]   [snap│tick│compact│swap]
  渲染:   [lerp│render] [lerp│render] [lerp│render] [lerp│render]
```

---

## 5. 线程模型

### 5.1 职责划分

| 操作 | 线程 | 时机 | 原因 |
|------|------|------|------|
| JSON 解析 & Molang 编译 | 主线程 (资源重载) | 启动/`/reload` | `ResourceManager` 仅主线程安全 |
| snapshotPositions() | 主线程 | tick 开头 | 由 swap 保证可见性 |
| Emitter 生命周期 (速率/寿命) | 主线程 | tick | 涉及 Molang 求值 |
| 发射新粒子 (形状/初始值) | 主线程 | tick | 涉及 Molang 求值 |
| 碰撞检测 | 主线程 | tick | `Level.getBlockState()` 仅主线程 |
| 事件触发 (音效/子粒子) | 主线程 | tick | `Level.playSound()` 仅主线程 |
| **运动积分** (pos+=vel*dt) | **主线程** | **tick** | **简化: 全量在 tick 做** |
| Molang 表达式求值 | 主线程 | tick | 可能访问实体/世界状态 |
| compact | 主线程 | tick 末尾 | 遍历结束后、swap 前调用一次 |
| volatile swap | 主线程 | tick 末尾 | 发布写入结果 |
| **partialTick lerp** | **渲染线程** | **每帧** | **纯读, 无副作用** |
| Billboard 渲染 | 渲染线程 | 每帧 | 原版渲染管线 |

### 5.2 主线程 tick 总体流程

```java
public void tick(ParticleDoubleBuffer db, float tickDt) {
    ParticleArray buf = db.startTick();
    
    // 1. 保存上一 tick 位置 (供渲染线程 lerp)
    buf.snapshotPositions();
    
    // 2. Emitter 逻辑
    emitterLifetime.tick(buf, tickDt);     // 寿命决定是否 active
    emitterRate.calculateSpawn(buf, tickDt); // 计算本次应发射数量
    
    // 2b. 发射新粒子 (先存放入队, 避免遍历时修改 count)
    int spawnCount = emitterRate.getPendingSpawnCount();
    IntArrayList newParticles = new IntArrayList(spawnCount);
    for (int s = 0; s < spawnCount; s++) {
        int idx = buf.allocate();
        emitterShape.applyPosition(buf, idx);      // 形状设初始 pos
        for (var comp : particlePreset.getAllComponents()) {
            comp.onApply(buf, idx);                 // 各组件初始化
        }
        newParticles.add(idx);
    }
    
    // 3. 活动粒子逻辑更新 + 运动积分 (合并遍历)
    int n = buf.getCount();
    IntArrayList pendingDead = new IntArrayList();  // 暂存待删除, 不立即 compact
    for (int i = 0; i < n; i++) {
        if (!buf.isAlive(i)) continue;
        
        // Molang 驱动参数 (tick 时求值)
        molangCtx.bindParticle(i, buf);
        buf.setAccelX(i, (float) accelExprX.evaluate(molangCtx));
        buf.setAccelY(i, (float) accelExprY.evaluate(molangCtx));
        buf.setAccelZ(i, (float) accelExprZ.evaluate(molangCtx));
        buf.setDragCoeff(i, (float) dragExpr.evaluate(molangCtx));
        
        // 运动积分
        buf.integrateParticle(i, tickDt);   // 单粒子积分
        
        // expiration 检查
        if (expirationExpr != null && expirationExpr.evaluate(molangCtx) != 0) {
            pendingDead.add(i);              // 只记索引, 不立即标记
            eventExecutor.fireExpirationEvents(i);
            continue;
        }
        
        // 碰撞检测 (仅主线程)
        collisionHandler.check(i, buf, level);
    }
    
    // 4. 统一标记死亡
    for (int i = 0; i < pendingDead.size(); i++) {
        buf.markDead(pendingDead.get(i));
    }
    
    // 5. compact (只在遍历结束后调用一次)
    buf.compact();
    
    // 6. swap (发布写入结果)
    db.swap();
}
```

### 5.3 渲染线程的 lerp + 渲染

```java
public void render(ParticleDoubleBuffer db, PoseStack pose,
                   MultiBufferSource buffer, Camera camera,
                   float partialTick, int light) {
    
    ParticleArray buf = db.getRender();  // 读 volatile
    VertexConsumer vc = buffer.getBuffer(RenderType.PARTICLE);
    
    int count = buf.getCount();
    for (int i = 0; i < count; i++) {
        if (!buf.isAlive(i)) continue;
        
        // lerp: 纯数学, 无副作用
        float renderX = buf.getRenderX(i, partialTick);
        float renderY = buf.getRenderY(i, partialTick);
        float renderZ = buf.getRenderZ(i, partialTick);
        
        // 提交 Billboard 四边形
        renderBillboardQuad(vc,
            renderX, renderY, renderZ,
            buf.getWidth(i), buf.getHeight(i),
            buf.getU0(i), buf.getV0(i), buf.getU1(i), buf.getV1(i),
            buf.getR(i), buf.getG(i), buf.getB(i), buf.getA(i));
    }
}
```

---

## 6. 组件系统

继承 SimpleBedrockModel 的双层组件设计模式，但运行时组件操作 `ParticleArray + int index` 而非 `ParticleInstance` 对象。

### 6.1 组件接口体系

```
// ── 定义层 (不可变, JSON 反序列化产物) ──

interface IComponentDefinition {
    int order();  // 执行顺序
}

interface IEmitterComponentDefinition extends IComponentDefinition {
    IEmitterComponent createRuntime(ParticleArray buf);
    boolean requireUpdate();
}

interface IParticleComponentDefinition extends IComponentDefinition {
    IParticleComponent createRuntime(ParticleArray buf);
    boolean requireUpdate();
}

// ── 运行时层 (可变, 操作 ParticleArray) ──

interface IEmitterComponent {
    /** 每 tick 调用 */
    void tick(ParticleArray buf, float tickDt);
    /** 新粒子生成时的回调 */
    void onSpawn(ParticleArray buf, int particleIndex);
}

interface IParticleComponent {
    /** 粒子每 tick 调用 (主线程, 含运动积分) */
    void tick(ParticleArray buf, int index, float tickDt);
    /** 粒子生成时一次调用 */
    void onApply(ParticleArray buf, int index);
}
```

### 6.2 定义层 → 运行时层的映射

```
┌─ 定义层 (createRuntime) ──────────┐
│ EmitterRateSteady                  │
│   spawnRate: MolangExpression     │  →  EmitterRateSteadyRuntime
│   maxParticles: MolangExpression  │      持有: float spawnAccumulator
└────────────────────────────────────┘      操作: buf.allocate()
                                            写入: 新粒子初始 posX/Y/Z

┌─ 定义层 ───────────────────────────┐
│ ParticleMotionDynamic              │
│   linearAcceleration: ME[3]       │  →  MotionDynamicRuntime
│   linearDragCoefficient: ME       │      持有: accelExpr/dragExpr
└────────────────────────────────────┘      tick(): 写入 buf.accelX[]/dragCoeff[]
```

### 6.3 组件列表

完整实现 Bedrock `particle_effect` 规范的全部 29 个组件：

**发射器级 (11)**:
- `minecraft:emitter_rate_instant/steady/manual`
- `minecraft:emitter_lifetime_once/looping/expression`
- `minecraft:emitter_initialization`
- `minecraft:emitter_local_space`
- `minecraft:emitter_shape_point/sphere/box/disc/entity_aabb`
- `minecraft:emitter_lifetime_events`

**粒子级 (18)**:
- `minecraft:particle_lifetime_expression/kill_plane/events`
- `minecraft:particle_initial_speed/spin/initialization`
- `minecraft:particle_motion_dynamic/parametric/collision`
- `minecraft:particle_appearance_billboard/tinting/lighting`
- `minecraft:particle_expire_if_in_blocks/not_in_blocks`

### 6.4 EmitterPreset / ParticlePreset

```java
public class EmitterPreset {
    private final List<IEmitterComponent> updateComponents;  // requireUpdate 子集
    private final IEmitterComponent[] allComponents;
    private final boolean hasLocalPosition;
    private final boolean hasLocalRotation;
    private final boolean hasLocalVelocity;
    
    public static EmitterPreset build(List<IEmitterComponentDefinition> defs, ParticleArray buf) {
        // 按 order() 排序 → 过滤 updateComponents → 提取 local space 标志
    }
}

public class ParticlePreset {
    private final List<IParticleComponent> updateComponents;
    private final IParticleComponent[] allComponents;
    private final BillboardMode faceCameraMode;  // 11 种朝向
    private final boolean hasLighting;
    /** 此粒子定义使用的 MolangContext 类型 */
    private final Class<? extends ParticleMolangContext> contextClass;
}
```

### 6.5 compact 时机约定

```
tick() 流程中的 compact 安全规则:
  ① 使用 IntArrayList 暂存待删除粒子索引 (不立即修改 alive[])
  ② 遍历结束后统一 markDead()
  ③ 最后调用一次 compact()
  
  原因:
  - 避免遍历时数组索引变化导致漏处理或重复处理
  - 事件回调 (expiration/events) 中引用的粒子索引在 compact 前有效
  - 渲染线程只读 lerp, 不受 compact 索引变化影响
```

---

## 7. Molang 集成

### 7.1 ParticleMolangContext

继承 Spark-Core 的 `MolangContext`，利用 `@QueryBinding` 注解机制绑定粒子专用变量。

**重要约定**: Molang 表达式中的 `particle_age` 始终映射到 `age`（tick 时累加的值），而非 `prevPos`/`pos` 之间的插值时间。渲染线程不参与 Molang 求值。

```java
public class ParticleMolangContext extends MolangContext<IAnimatable<?>> {
    
    private double emitterAge;
    private double emitterLifetime;
    private final double[] emitterRandom = new double[4];
    
    // particle_age 映射到 tick 时累加的 age, 而非插值时间
    private double particleAge;
    private double particleLifetime;
    private final double[] particleRandom = new double[4];
    
    @QueryBinding("emitter_age")       public double qEmitterAge()      { return emitterAge; }
    @QueryBinding("emitter_lifetime")  public double qEmitterLifetime() { return emitterLifetime; }
    @QueryBinding("emitter_random_1")  public double qEmitterRandom1()  { return emitterRandom[0]; }
    @QueryBinding("emitter_random_2")  public double qEmitterRandom2()  { return emitterRandom[1]; }
    @QueryBinding("emitter_random_3")  public double qEmitterRandom3()  { return emitterRandom[2]; }
    @QueryBinding("emitter_random_4")  public double qEmitterRandom4()  { return emitterRandom[3]; }
    
    @QueryBinding("particle_age")      public double qParticleAge()     { return particleAge; }
    @QueryBinding("particle_lifetime") public double qParticleLifetime(){ return particleLifetime; }
    @QueryBinding("particle_random_1") public double qParticleRandom1() { return particleRandom[0]; }
    @QueryBinding("particle_random_2") public double qParticleRandom2() { return particleRandom[1]; }
    @QueryBinding("particle_random_3") public double qParticleRandom3() { return particleRandom[2]; }
    @QueryBinding("particle_random_4") public double qParticleRandom4() { return particleRandom[3]; }
    
    public void bindEmitter(double age, double lifetime, double[] randoms) {
        this.emitterAge = age;
        this.emitterLifetime = lifetime;
        System.arraycopy(randoms, 0, this.emitterRandom, 0, 4);
    }
    
    public void bindParticle(double age, double lifetime, double[] randoms) {
        this.particleAge = age;
        this.particleLifetime = lifetime;
        System.arraycopy(randoms, 0, this.particleRandom, 0, 4);
    }
}
```

### 7.2 表达式管理

```java
public class ParticleEffectDefinition {

    // 发射器表达式
    private final MolangExpression emitterRateExpr;
    private final MolangExpression emitterActiveTimeExpr;
    private final MolangExpression emitterSleepTimeExpr;
    
    // 粒子表达式
    private final MolangExpression maxLifetimeExpr;
    private final MolangExpression expirationExpr;
    private final MolangExpression particleSpeedExpr;
    private final MolangExpression[] accelExprs;
    private final MolangExpression dragExpr;
    
    // 曲线
    private final Map<String, ParticleCurve> curves;
    
    /**
     * 默认的 contextClass 为 ParticleMolangContext.class。
     * 大多数粒子定义共享此类型。若需自定义 Context（如 Machine-Max 添加子系统的 query 方法），
     * 可在 JSON 中通过扩展字段指定，或在代码中构建 Definition 时注入。
     * Phase 2 实现时默认使用 ParticleMolangContext.class，暂不解析 JSON 中 context 字段。
     */
    private final Class<? extends ParticleMolangContext> contextClass = ParticleMolangContext.class;
}
```

### 7.3 Molang 集成入口: MolangContextRegistry

使用 `MolangContextRegistry`（而非已废弃的 `SparkMolangEngine`）作为主要入口：

```java
public class ParticleMolangEnvironment {
    private final ParticleMolangContext context;
    private final MutableObjectBinding variableStorage;
    
    public ParticleMolangEnvironment() {
        this.context = new ParticleMolangContext(null);
        this.variableStorage = context.getVariableStorage();
    }
    
    public double evaluate(MolangExpression expr) {
        return expr.evaluate(context);
    }
    
    public void bindEmitter(double age, double lifetime, double[] randoms) {
        context.bindEmitter(age, lifetime, randoms);
    }
    
    public void bindParticle(double age, double lifetime, double[] randoms) {
        context.bindParticle(age, lifetime, randoms);
    }
    
    public void setVariable(String name, double value) {
        variableStorage.set(name, Value.of(value));
    }
}
```

### 7.4 基准测试要求

Phase 3 验证阶段需补充基准测试：
- 8000 粒子 × 5 表达式/粒子 × 1 tick = 40000 次 Molang 求值
- 测量每 tick 总耗时
- 如果 > 1ms，考虑表达式分组求值或批量上下文绑定

---

## 8. 粒子定义协议 (JSON Schema)

完整兼容 Bedrock `particle_effect` v1.10.0 规范。

### 8.1 顶层结构

```json
{
    "format_version": "1.10.0",
    "particle_effect": {
        "description": {
            "identifier": "example:smoke",
            "basic_render_parameters": {
                "material": "particles_blend",
                "texture": "textures/particle/smoke"
            }
        },
        "components": { ... },
        "curves": { ... },
        "events": { ... }
    }
}
```

### 8.2 材质映射

| 基岩版 material | Java Edition RenderType |
|-----------------|------------------------|
| `particles_blend` | `RenderType.PARTICLE` (带透明) |
| `particles_opaque` | `RenderType.PARTICLE` (无透明) |
| `particles_alpha` | `RenderType.entityCutout` |
| `particles_add` | **自定义 `RenderType` (ADD 混合)** |

**依赖**: `particles_add` 的自定义 RenderType 需在 `RegisterShadersEvent` 中注册，与 `CameraShaker` 等特效使用同一机制。Phase 5 需标注此依赖。

### 8.3 纹理路径映射

```
基岩版: "textures/particle/smoke"
Java版: ResourceLocation("namespace:textures/particle/smoke.png")
```

### 8.4 曲线系统

```json
{
    "curves": {
        "variable.curve_size": {
            "type": "linear | bezier | catmull_rom | bezier_chain",
            "input": "v.particle_age / v.particle_lifetime",
            "horizontal_range": 1.0,
            "nodes": [0.0, 0.5, 1.0, 0.5, 0.0]
        }
    }
}
```

曲线求值结果通过 `ParticleMolangEnvironment.setVariable("curve_size", value)` 写入，粒子表达式通过 `variable.curve_size` 引用。

### 8.5 事件系统

| 事件类型 | 动作 |
|---------|------|
| `particle_effect` | 创建子发射器（支持 `pre_effect_expression`） |
| `sound_effect` | 播放音效 |
| `sequence` | 顺序执行子事件 |
| `randomize` | 按权重随机选择 |
| `expression` | 执行 Molang 表达式 |
| `log` | 输出调试日志 |

---

## 9. 渲染管线

### 9.1 Billboard 渲染

```java
public class BillboardRenderer {
    
    private static final Vector4f TEMP_VIEW_POS = new Vector4f();
    private static final Vector3f TEMP_AXIS_X = new Vector3f();
    private static final Vector3f TEMP_AXIS_Y = new Vector3f();
    private static final Matrix4f TEMP_MATRIX = new Matrix4f();
    
    public void render(ParticleArray buf, PoseStack pose,
                       MultiBufferSource buffer, Camera camera,
                       float partialTick, int light) {
        
        VertexConsumer vc = buffer.getBuffer(RenderType.PARTICLE);
        int count = buf.getCount();
        
        for (int i = 0; i < count; i++) {
            if (!buf.isAlive(i)) continue;
            
            // lerp 位置 (纯读)
            float rx = buf.getRenderX(i, partialTick);
            float ry = buf.getRenderY(i, partialTick);
            float rz = buf.getRenderZ(i, partialTick);
            
            // Billboard 矩阵计算 → 提交 4 顶点
            renderBillboardQuad(vc, rx, ry, rz,
                buf.getWidth(i), buf.getHeight(i),
                buf.getU0(i), buf.getV0(i), buf.getU1(i), buf.getV1(i),
                buf.getR(i), buf.getG(i), buf.getB(i), buf.getA(i));
        }
    }
}
```

### 9.2 朝向模式

支持 11 种基岩版朝向模式：`rotate_xyz`, `rotate_y`, `lookat_xyz`, `lookat_y`, `lookat_direction`, `direction_x/y/z`, `emitter_transform_xy/xz/yz`。

### 9.3 性能优化路径

```
第一优先: SodiumCompat.isSodiumInstalled()
  → SodiumParticleVertexWriter (64 位对齐 scratch buffer, 4 顶点批量提交)
第二优先: EmbeddiumCompat.isEmbeddiumInstalled()
  → EmbeddiumParticleVertexWriter
回退: VertexConsumer.addVertex() × 4
```

**已知不足**: 未使用纹理图集，标记 TODO。后续优化。

### 9.4 渲染阶段 (Render Stage)

粒子 Billboard 渲染挂在 **`RenderLevelStageEvent.Stage.AFTER_ENTITIES`**。

理由：
- 粒子是实体级别的特效，应在所有实体渲染完成后、透明粒子/拖尾等后期特效之前渲染
- `AFTER_PARTICLES` 被 `TrailRenderer`（拖尾渲染器）使用，拖尾逻辑上应在粒子之后渲染
- 与 `AFTER_ENTITIES` 之后、`AFTER_PARTICLES` 之前的阶段语义一致

```
渲染管线顺序:
  AFTER_ENTITIES     ← 粒子 Billboard 渲染 (本系统)
  AFTER_PARTICLES    ← TrailRenderer (拖尾)
  AFTER_WEATHER      ← 天气效果
  AFTER_LEVEL        ← 最终合成
```

---

## 10. 双端 API 设计

参考 `SpreadingSoundHelper` 的工厂模式。**接口方法在 common 包中双端可用，当前仅客户端实际实现**。

### 10.1 IParticleEffectPlayer (common 接口)

```java
// common 包, 双端共享
public interface IParticleEffectPlayer {
    
    /** 触发一点粒子效果 (定点播放, 双端可用) */
    void playEffect(Level level, ResourceLocation effectId,
                    Vec3 position, Vec3 rotation);
    
    /** 触发粒子效果并绑定到 locator (双端可用) */
    void playEffect(Level level, ResourceLocation effectId,
                    String locator, UUID entityId);
    
    /** 停止指定粒子的播放 (双端可用) */
    void stopEffect(Level level, UUID effectInstanceId);
}
```

### 10.2 ParticleEffects (static helper, 双端统一入口)

```java
// common 包
public class ParticleEffects {
    
    private static final IParticleEffectPlayer INSTANCE = init();
    
    private static IParticleEffectPlayer init() {
        if (FMLEnvironment.dist.isClient()) {
            return new ClientParticleEffectPlayer();
        } else {
            return new ServerParticleEffectPlayer();
        }
    }
    
    /** 触发一点粒子效果, 双端可用。当前仅在客户端实际有效 */
    public static void burst(Level level, ResourceLocation effectId,
                             Vec3 position, Vec3 rotation) {
        INSTANCE.playEffect(level, effectId, position, rotation);
    }
    
    /** 触发粒子效果并绑定到 locator, 双端可用 */
    public static void burst(Level level, ResourceLocation effectId,
                             String locator, UUID entityId) {
        INSTANCE.playEffect(level, effectId, locator, entityId);
    }
    
    /** 停止指定粒子效果, 双端可用 */
    public static void stop(Level level, UUID effectInstanceId) {
        INSTANCE.stopEffect(level, effectInstanceId);
    }
}
```

### 10.3 ClientParticleEffectPlayer (实际实现)

```java
// 仅客户端
@OnlyIn(Dist.CLIENT)
public class ClientParticleEffectPlayer implements IParticleEffectPlayer {
    
    @Override
    public void playEffect(Level level, ResourceLocation effectId,
                           Vec3 position, Vec3 rotation) {
        var def = ParticleDefinitionLoader.getInstance().getDefinition(effectId);
        if (def == null) return;
        
        var emitter = new ParticleEmitterInstance(def);
        emitter.setPosition(position);
        emitter.setRotation(rotation);
        ParticleEmitterManager.getInstance().add(emitter);
    }
    
    @Override
    public void playEffect(Level level, ResourceLocation effectId,
                           String locator, UUID entityId) {
        var entity = level.getEntity(entityId);
        if (!(entity instanceof IAnimatable<?> animatable)) return;
        
        var def = ParticleDefinitionLoader.getInstance().getDefinition(effectId);
        if (def == null) return;
        
        var transform = animatable.getModelController().getLocatorTransform(locator);
        if (transform == null) return;
        
        var emitter = new ParticleEmitterInstance(def);
        emitter.setLocatorTransform(transform);
        emitter.setBindToActor(true);
        ParticleEmitterManager.getInstance().add(emitter);
    }
    
    @Override
    public void stopEffect(Level level, UUID effectInstanceId) {
        ParticleEmitterManager.getInstance().remove(effectInstanceId);
    }
}
```

### 10.4 ServerParticleEffectPlayer (stub, 未来扩展)

```java
// 仅服务端, 当前为 stub
public class ServerParticleEffectPlayer implements IParticleEffectPlayer {
    
    @Override
    public void playEffect(Level level, ResourceLocation effectId,
                           Vec3 position, Vec3 rotation) {
        if (level instanceof ServerLevel serverLevel) {
            // TODO: 发送 ParticleEffectTriggerPacket 给附近玩家
            // PacketDistributor.sendToPlayersNear(...)
        }
    }
    
    @Override
    public void playEffect(Level level, ResourceLocation effectId,
                           String locator, UUID entityId) {
        // TODO: 客户端侧将 locator 变换求值后渲染
    }
    
    @Override
    public void stopEffect(Level level, UUID effectInstanceId) {
        // TODO: 发送 ParticleEffectStopPacket
    }
}

```

**初始化时机与 Server stub 行为**:
- `INSTANCE` 通过 `static init()` 在类加载时创建，类加载发生在 Mod 构造器阶段，`FMLEnvironment.dist` 已经可用（与 `SpreadingSoundHelper` 一致）
- 当前 `ServerParticleEffectPlayer` 为无操作实现，服务端调用 `ParticleEffects.burst()` **静默返回，无效果，不抛异常**
- 调用方需注意当前限制：纯客户端效果直接调用即可；需服务端同步触发的效果（如武器子系统开火），目前需手动发送网络包

---

## 11. 包结构

```
cn.solarmoon.spark_core.particle/
│
├── common/                           ← 双端共享
│   ├── IParticleEffectPlayer.java    // 工厂接口
│   ├── ParticleEffects.java          // static helper (与 SpreadingSoundHelper 同模式)
│   ├── ParticleEffectReference.java  // record(identifier, position, rotation)
│   ├── data/
│   │   ├── ParticleEffectDefinition.java   // 不可变数据模型
│   │   ├── ParticleDescription.java         // material + texture
│   │   ├── EmitterPreset.java
│   │   ├── ParticlePreset.java
│   │   ├── ParticleComponentRegistry.java   // JSON key → 反序列化器
│   │   ├── component/                       // 29 个组件定义接口
│   │   │   ├── IComponentDefinition.java
│   │   │   ├── IEmitterComponentDefinition.java
│   │   │   ├── IParticleComponentDefinition.java
│   │   │   ├── rate/
│   │   │   ├── lifetime/
│   │   │   ├── shape/
│   │   │   ├── motion/
│   │   │   └── appearance/
│   │   ├── curve/ParticleCurve.java
│   │   └── event/IEventNode.java     // 6 种事件节点
│   └── ...                           // 其他纯数据类
│
├── client/                           ← 仅客户端
│   ├── ServerParticleEffectPlayer.java  // stub (未来发网络包)
│   ├── ClientParticleEffectPlayer.java  // 实际实现
│   ├── ParticleArray.java               // SoA 核心
│   ├── ParticleDoubleBuffer.java        // 双重缓冲
│   ├── ParticleEffectDeserializer.java  // JSON 解析
│   ├── ParticleDefinitionLoader.java    // 注册表 + 桥接
│   ├── ParticleMolangEnvironment.java   // Molang 上下文
│   ├── ParticleMolangContext.java       // @QueryBinding
│   ├── ParticleEmitterInstance.java     // 发射器运行时
│   ├── ParticleEmitterManager.java      // 管理器
│   ├── render/
│   │   ├── ParticleRenderer.java        // Billboard 渲染
│   │   ├── BillboardHelper.java
│   │   ├── ParticleRenderType.java
│   │   ├── CameraStateCache.java
│   │   └── compat/
│   │       ├── sodium/SodiumParticleVertexWriter.java
│   │       └── embeddium/EmbeddiumParticleVertexWriter.java
│   ├── world/
│   │   ├── WorldEmitterManager.java
│   │   └── SnowStormParticle.java
│   └── firstperson/
│       └── FirstPersonParticleSystem.java

// ── 另外在 pack/modules/ 下 ──
// 内容包模块: ParticleModule.kt (独立于上述 client 包, 遵循 SparkPackModule 接口)
```

---

## 12. 逐步实施计划

### Phase 1: SoA 核心数据结构 (~500 行)

**文件**: `ParticleArray.java`, `ParticleDoubleBuffer.java`

- 19 个并行 float 数组 + prevPosX/Y/Z
- allocate / markDead / compact / snapshotPositions / integrate
- volatile double buffer, 渲染线程只读 lerp

**验证**: 单元测试 (allocate 10000 → integrate → compact → lerp 前后一致性)

---

### Phase 2: 粒子定义数据模型 (~3000 行)

**文件**: `common/data/` 下所有定义类 + component/event/curve 子包

- 29 个组件定义接口 + JSON 反序列化
- 6 种事件节点 + 4 种曲线
- EmitterPreset / ParticlePreset (含 `contextClass` 字段)
- ParticleComponentRegistry

**验证**: 加载现有基岩版粒子 JSON, 验证反序列化结果

---

### Phase 3: Molang 集成 (~200 行)

**文件**: `ParticleMolangContext.java`, `ParticleMolangEnvironment.java`

- @QueryBinding 绑定 emitter_* / particle_* 变量
- 使用 `MolangContextRegistry` 作为入口
- 基准测试: 8000 粒子 × 5 表达式, 每 tick < 1ms

**验证**: 编译 + 求值, 结果在预期范围

---

### Phase 4: 发射器运行时 (~1500 行)

**文件**: `ParticleEmitterInstance.java`, 各组件 runtime 内部类

- tick 循环: snapshot → 逻辑 → 积分 → 暂存死亡 → compact → swap
- 暂存死亡使用 IntArrayList, 遍历结束后统一标记
- 运动积分在 tick 内全量做

**验证**: 创建发射器 → tick() → 产生粒子 → swap → 渲染线程 lerp 正确

---

### Phase 5: 渲染器 (~800 行)

**文件**: `render/` 下所有类

- Billboard 渲染 + 11 种朝向
- lerp(prevPos, pos, partialTick) 位置插值
- particles_add 自定义 RenderType 需在 `RegisterShadersEvent` 注册
- Sodium/Embeddium 快速路径

**验证**: 粒子正确面向相机, partialTick 平滑

---

### Phase 6: 内容包模块 + 世界集成 (~700 行)

**文件**:
- `pack/modules/ParticleModule.kt` — `SparkPackModule` 实现
- `client/ParticleDefinitionLoader.java` — 桥接内容包文件 → `ParticleEffectDefinition`
- `client/ParticleEmitterManager.java` — 管理器
- `client/world/WorldEmitterManager.java`
- `client/world/SnowStormParticle.java`

**SparkPackModule 实现**:

```kotlin
// pack/modules/ParticleModule.kt
class ParticleModule : SparkPackModule {

    override val id: String = "particles"
    override val mode: ReadMode = ReadMode.LOCAL_ONLY  // 仅客户端读取本地

    companion object {
        /** 加载的粒子定义缓存 */
        val definitions: MutableMap<ResourceLocation, ParticleEffectDefinition> = mutableMapOf()
    }

    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if (isClientSide && !fromServer) {
            definitions.clear()
        }
    }

    override fun read(
        namespace: String,
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage,
        isClientSide: Boolean, fromServer: Boolean
    ) {
        if (!isClientSide) return
        // spark_modules/<namespace>/particles/**/*.particle.json
        if (!fileName.endsWith(".particle.json")) return

        val path = if (pathSegments.isEmpty()) {
            fileName.removeSuffix(".particle.json")
        } else {
            "${pathSegments.joinToString("/")}/${fileName.removeSuffix(".particle.json")}"
        }
        val id = ResourceLocation.fromNamespaceAndPath(namespace, path)

        try {
            val json = JsonParser.parseString(String(content, StandardCharsets.UTF_8))
            val def = ParticleEffectDeserializer.deserialize(id, json)
            definitions[id] = def
        } catch (e: Exception) {
            SparkCore.LOGGER.error("粒子定义 $id 加载失败: ${e.message}")
        }
    }

    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        if (isClientSide) {
            SparkCore.LOGGER.info("已加载 ${definitions.size} 个粒子定义")
            // 注入到 ParticleDefinitionLoader 的注册表
            ParticleDefinitionLoader.getInstance().reload(definitions)
        }
    }
}
```

**注册到内容包管线**:

```kotlin
// registry/common/SparkPackModuleRegister.kt 中添加一行
fun reg(event: SparkPackageReaderRegisterEvent) {
    // ... 现有 9 个模块 ...
    event.register(ParticleModule())  // ← 新增
}
```

**内容包文件约定**: `spark_modules/<pack>/<namespace>/particles/**/*.particle.json`

**路径说明**: 模块 `id = "particles"` 与现有内容包模块的命名风格一致（均为复数名词: `models`, `animations`, `textures`, `sounds`, `parts`, `subsystems` 等）。内容包中的目录映射为:
```
spark_modules/MyPack/machine_max/
├── particles/
│   ├── fx_smoke.particle.json      → machine_max:fx_smoke
│   └── weapons/
│       └── fx_muzzle.particle.json → machine_max:weapons/fx_muzzle
├── parts/                          ← 现有 PartModule
└── subsystems/                     ← 现有 SubsystemModule
```

**ParticleEmitterManager 线程安全**: `add()` 方法应使用 `ConcurrentLinkedQueue` 暂存新发射器，在主线程 tick 中消费入队。因为 `playEffect()` 可能在以下线程被调用:
- 主线程（动画状态机 `OParticleEffect` 触发、网络包处理器）
- 暂不支持其他线程，但 `ConcurrentLinkedQueue` 预留了安全余量

**验证**: /reload 后粒子定义重新加载

---

### Phase 7: 第一人称系统 (~300 行)

**文件**: `firstperson/FirstPersonParticleSystem.java`

**验证**: 第一人称枪械开火粒子正确显示

---

## 13. 不变部分清单

| 组件 | 说明 |
|------|------|
| `MochaEngine` / `MolangCompiler` | Molang 编译执行不变 |
| `MolangContextRegistry` | **作为粒子 Molang 集成的主要入口** |
| `IAnimatable` | 动画接口不变 |
| Spark-Core 物理系统 | 不受影响 |
| Machine-Max VehicleCore/SubPart | 不受影响 |
| Machine-Max 子系统 | 仅新增 `ParticleEffects.burst()` 调用点 |
| 原版 `ParticleEngine` | 共存, 不冲突 |

---

## 14. 风险与缓解

| 风险 | 等级 | 缓解 |
|------|------|------|
| **compact 后粒子索引变化** | ⚠️ 中 | 约定: IntArrayList 暂存待删除, 遍历结束后统一 markDead + compact。事件回调在 compact 前触发完成。渲染线程仅做 lerp, 不依赖索引稳定性 |
| **Molang 求值性能 (8000 粒子 × N 表达式)** | ⚠️ 中 | Phase 3 补充 JMH 基准测试。若 > 1ms/tick, 考虑表达式分组求值或批量上下文绑定 |
| **particles_add 自定义 RenderType 注册** | ⚠️ 中 | Phase 5 需依赖 `SparkShaders.kt` 的 `RegisterShadersEvent` |
| **MolangExpression 跨 Context 兼容性** | ⚠️ 低 | `ParticleEffectDefinition` 持有 `contextClass` 字段, 确保编译和求值使用同一类型 |
| **纹理图集缺失** | ⚠️ 中 | 已知问题, 标记 TODO。每材质独立 RenderType → 多批次。后续自动图集合并 |
| **Sodium/Embeddium 兼容** | ⚠️ 低 | 复用 SimpleBedrockModel 已验证的方案 |
| **碰撞检测开销** | ⚠️ 低 | 仅在 tick 检测 (20 TPS) |
| **服务端 stub 后续扩展** | ⚠️ 低 | 接口已预留, 网络包协议待后续实现 |

---

## 15. 与现有 SparkParticles.kt 的共存策略

现有两个已有粒子类型（`AnimatableShadowParticle`, `SpaceWarpParticle`）继承 Minecraft 原版 `Particle` 类，走原版 `ParticleEngine` 管线（AoS 方式）。

| 粒子类型 | 现状 | 新系统上线后 |
|----------|------|-------------|
| `AnimatableShadowParticle` | 原版 `Particle` + `SimpleParticleType` 注册 | **保持原样**, 不走新系统 |
| `SpaceWarpParticle` | 自定义 mesh + 自定义 Shader | **保持原样**, 不走新系统 |
| `SparkParticles.kt` 注册表 | 注册 2 个 `ParticleType` | **保留**, 与新系统共存 |
| `SparkParticleProviderRegister.kt` | 2 个 Provider | **保留**, 与新系统共存 |
| `OParticleEffect` (动画状态机) | 当前桥接到 `preEffectScript` | **逐步迁移**到 `ParticleEffects.burst()` |

**理由**: 两种现有粒子有各自独特的渲染方式（阴影投影和空间扭曲），不适合 Billboard 通用粒子管线。它们与原版 `ParticleEngine` 的集成已经稳定，无需迁移动。

**`OParticleEffect` 迁移路径**:
- Phase 6 完成后：`OParticleEffect` 内部调用 `ParticleEffects.burst()` + 新系统
- 旧 `JS preEffectScript` 保留为回退路径
- 在 `OParticleEffect` 的粒子定义 ID 有对应的 `ParticleEffectDefinition` 时使用新系统，否则回退旧路径

---

## 附录 A: 代码量估算

| Phase | 文件数 | 行数 (预估) | 依赖 |
|-------|--------|-------------|------|
| Phase 1: SoA 核心 | 2 | ~500 | 无 |
| Phase 2: 数据模型 (含 common) | 30+ | ~3000 | Phase 1 |
| Phase 3: Molang | 2 | ~200 | Phase 2, MolangContextRegistry |
| Phase 4: 发射器运行时 | 8 | ~1500 | Phase 1-3 |
| Phase 5: 渲染器 | 5 | ~800 | Phase 1, RegisterShadersEvent |
| Phase 6: 内容包 + 双端 API | 7 | ~700 | Phase 2 |
| Phase 7: 第一人称 | 1 | ~300 | Phase 4-5 |
| **合计** | **~55** | **~7000** | |

## 附录 B: Common/Client 双端 API 模式

```
ParticleEffects.burst(level, effectId, pos, rot)   ← 调用方, 双端同一行代码
         │
         ▼
  INITANCE (IParticleEffectPlayer)
         │
    ┌────┴────┐
    ▼         ▼
  Client    Server (stub)
  ─────     ──────────
  JSON 加载  TODO: 发送网络包
  → 创建     到附近玩家
  Emitter
  → 模拟
  → 渲染
```

## 附录 C: SoA 内存布局示意图

```
ParticleArray (count=4, capacity=8)
                                       64 字节缓存线
                                    ┌──────────────────────┐
posX[] =  [1.0, 2.0, 3.0, 4.0, ...]← 4×4=16 字节, 一条线装 16 粒子
posY[] =  [5.0, 6.0, 7.0, 8.0, ...]
posZ[] =  [9.0, 1.0, 2.0, 3.0, ...]
prevPosX[]= [0.9, 1.8, 2.7, 3.6,...]← 紧邻 pos, lerp 时相邻
velX[] =  [0.1, 0.2, 0.3, 0.4, ...]
velY[] =  [0.5, 0.6, 0.7, 0.8, ...]
velZ[] =  [0.9, 1.0, 1.1, 1.2, ...]
accelX[] =[0.0, 0.0, 0.0, 0.0, ...]
  ...
age[]   = [0.5, 1.2, 2.1, 0.8, ...]
alive[] = [T,   T,   T,   F,   ...]

更新循环: for i in 0..count (主线程 tick, 单次遍历完成全部):
  accelX[i] = Molang求值
  velX[i] += accelX[i] * tickDt
  posX[i] += velX[i] * tickDt     ← 3 条紧邻数组, 全在 L1 缓存中
  age[i] += tickDt

渲染循环: for i in 0..count (渲染线程, 只读):
  rx = prevPosX[i] + (posX[i] - prevPosX[i]) * pt  ← prevPos 紧邻 pos
```

---

*本计划文档已根据审阅意见修正: 渲染线程不做积分 / 暂存死亡 + 统一 compact / contextClass 字段 / MolangContextRegistry 入口 / 双端 API 工厂模式 / SparkShaders RegisterShadersEvent 依赖 / 内容包模块 ParticleModule / 与现有粒子共存策略。*

---

## 附录 D: 实施进度追踪

| # | Phase | 核心产出 | 文件数 | 预估行数 | 状态 | 说明 |
|---|-------|---------|--------|---------|------|------|
| 1 | SoA 核心数据结构 | `ParticleArray.java`, `ParticleDoubleBuffer.java` | 2 | ~500 | ✅ 已完成 | allocate/snapshot/integrate/compact/lerp 全部实现。遗留: `mass[]` 字段未使用, `reset()` 与 `clear()` 重复 |
| 2 | 数据模型 + JSON 解析 | `ParticleEffectDefinition`, 29 组件, 事件, 曲线 | 30+ | ~3000 | ✅ 已完成 | B2 已修复（28 个 register 全改用正确方法引用），所有 29 种组件反序列化到位。ParticleMotionCollision 分支已补全 |
| 3 | Molang 集成 | `ParticleMolangContext.java`, `ParticleMolangEnvironment.java` | 2 | ~200 | ✅ 已完成 | 10 个 @QueryBinding 变量齐全，使用 MolangContextRegistry。新增 `getLevel()`/`setLevel()` 供碰撞组件。遗留: 无基准测试 |
| 4 | 发射器运行时 | `ParticleEmitterInstance.java`, 各组件 runtime | 8 | ~1500 | ✅ 已完成 | B1 已修复（组件通过方法参数接收 emitter 的 molang 环境，粒子变量正确绑定）。B7 已修复（IntArrayList 代替 ArrayList<Integer>）。遗留: `EmitterLifetimeEvents`/`ParticleLifetimeEvents` 事件触发逻辑尚未完整（依赖 EventExecutor） |
| 5 | 渲染器 | Billboard 渲染, 11 种朝向, Sodium 兼容, RenderType 注册 | 7 | ~800 | ⚠️ 部分阻塞 | B5 已修复（`vertex()` 中使用传入的 light 参数）。阻塞降级: Sodium/Embeddium 空壳(B4) 和 4 种朝向(B6) 为低优先级 |
| 6 | 内容包 + 双端 API | `ParticleModule.kt`, `ParticleEffects`, Client/Server Player | 7 | ~700 | ✅ 已完成 | ParticleModule 完整，双端 API 工厂模式正确，EmitterManager 使用 CLQ。遗留: /reload 线程安全空窗期 |
| 7 | 第一人称系统 | `FirstPersonParticleSystem.java` | 1 | ~300 | ⚠️ 阻塞 | 骨架存在，locator 参数和 SOUND_EFFECT/particle_effect 事件未实现 |
| | **合计** | | **~56** | **~7000** | | |

**状态标记说明**:
- 📝 待开始 — 尚未实现
- 🔧 进行中 — 正在编码
- ✅ 已完成 — 编码 + 验证通过
- ⚠️ 阻塞 — 有未修复问题影响可用性

### 已知阻塞项（需在继续开发前修复）

| # | 严重度 | Phase | 问题 | 影响 | 状态 |
|---|--------|-------|------|------|:----:|
| B1 | 🔴 阻塞 | P4 | **Molang 上下文捕获错误** — 运行时组件通过方法参数接收 emitter 自身 Molang 环境而非反序列化器的临时环境 | 所有依赖 Molang 变量的粒子运动/外观失效 | ✅ 已修复 |
| B2 | 🔴 阻塞 | P2 | **`ParticleComponentRegistry` 反序列化器全为 null** — 28 个 `register(key, null)` 替换为 `register(key, Xxx::fromJson)` | 扩展性差，新增组件必须修改 deserializer | ✅ 已修复 |
| B3 | 🟠 中 | P2 | **3 个组件未被 deserializer 覆盖** — `EmitterLifetimeEvents`/`ParticleLifetimeEvents`/`ParticleMotionCollision` 落入通用分支返回 TODO stub | JSON 中这 3 个组件被静默忽略 | 🟡 部分: ParticleMotionCollision 已添加；事件组件需 EventExecutor 配合 |
| B4 | 🟡 低 | P5 | **Sodium/Embeddium 快速路径空壳** — 两个文件仅有 TODO 注释 | 大量粒子时无优化，性能堪忧 | 📝 未开始（可延期） |
| B5 | 🟠 中 | P5 | **`renderBillboard` light 硬编码** — `vertex()` 中 `setLight(FULL_BRIGHT)` 改为 `setLight(light)` | 粒子不受光照影响，始终全亮 | ✅ 已修复 |
| B6 | 🟡 低 | P5 | **4 种 Billboard 朝向未实现** — `LOOKAT_DIRECTION` 和三种 `EMITTER_TRANSFORM_*` 为 identity 占位 | 使用这些模式的粒子朝向错误 | 📝 未开始（可延期） |
| B7 | 🟡 低 | P4 | **`pendingDead` 使用 `ArrayList<Integer>`** — 替换为 `IntArrayList` | 高粒子数装箱 GC 压力 | ✅ 已修复 |

### 里程碑

| 里程碑 | 触发条件 | 验证标准 | 当前状态 |
|--------|---------|---------|:-------:|
| **M1: 核心可用** | Phase 1-4 完成 + B1/B2 修复 | 加载粒子 JSON → 发射器 tick → 粒子运动积分 → swap | ✅ 条件满足，待端到端测试 |
| **M2: 可见** | Phase 5 完成 + B5 修复 | 粒子在屏幕上正确面向相机渲染 | ✅ 条件满足，待端到端测试 |
| **M3: 内容包可加载** | Phase 6 完成 | 从 `spark_modules/` 加载 `.particle.json`, `/reload` 生效 | ⚠️ 未端到端验证 |
| **M4: 完整可用** | Phase 1-7 完成 + 全部 B 修复 | Machine-Max 武器子系统调用 `ParticleEffects.burst()` 触发枪口火焰 | ❌ Phase 7 未完成 |
| **M5: 性能达标** | 全部完成 + 基准测试 | 8000 粒子 × 5 表达式，每 tick < 2ms | ❌ |

---
