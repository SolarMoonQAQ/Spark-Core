# 物理线程跑图性能优化计划

## 问题

跑图时新生成区块的地形刚体建立过程给物理线程带来显著压力，低配 CPU（i5 2.4GHz）出现严重卡顿降速。

## 根因分析

### 当前流程

```
主线程 requestStep()
├─ updateBuild(boundingBoxes)
│   └─ PhysicsChunk.load() → startAsyncBuild() × N
│       └─ 协程投递到 terrainBuilderScope（2 线程池）
│           └─ buildCollisionShape()
│               └─ BlockMerger 三阶段合并 + 形状构建
│               └─ 完成后 → submitImmediateTask { createPhysicsBody() }  ← 投递到物理线程
│
├─ updateActivation(boundingBoxes)
│   └─ 范围内 section → activate()
│       └─ addPhysicsBody() → submitImmediateTask { world.addCollisionObject() }  ← 再投递
│
└─ physicsTickChannel.send(Unit)

物理线程 run()
├─ processTasks(PPhase.ALL)    ← 执行上述所有提交的任务
│   ├─ createPhysicsBody() × N   → native 内存分配 + 子形状复制
│   ├─ addCollisionObject() × N  → Broadphase DBVT 插入（每个含几十个子形状）
│   └─ body.collisionShape = x   → 触发 Broadphase 内部重建
├─ world.update()               ← Bullet 物理步进
├─ processTasks(PPhase.ALL)
└─ processTasks(PPhase.POST)
```

### 瓶颈定位

物理线程变慢不是因为 `world.update()` 本身，而是 **`processTasks()` 中执行的大量 native Bullet 操作**：

- **`createPhysicsBody()`** — 实际不需要物理线程。它只做 Java 堆上的字段赋值 + native 内存分配，不涉及 `PhysicsSpace` / `Broadphase`。
- **`addCollisionObject()`** — 需要在物理线程。每个刚体的 CompoundCollisionShape 含 10-50+ 子形状，全要插入 DBVT 树。
- **`body.collisionShape = newShape`** — 需要在物理线程。触发 Broadphase 内部重建。

跑图时 216 个 section 的 `createPhysicsBody()` + `addCollisionObject()` 全部塞进 `processTasks` 串行执行，单 tick 时间暴增 → 触发动态降频 → 正反馈恶化。

## 优化方案

### 核心思路

**将纯 Java 操作（`createPhysicsBody()`）从物理线程移到地形构建线程，物理线程只做真正的 native 最后一步。**

```
优化前：
构建线程 ──buildShape──→ submitImmediateTask ──→ 物理线程 { createBody() + addToWorld() }

优化后：
构建线程 ──buildShape──→ createBody() ──→ submitImmediateTask ──→ 物理线程 { addToWorld() }
```

### 涉及文件

| 文件 | 改动 |
|------|------|
| `physics/terrain/PhysicsChunkSection.kt` | `startAsyncBuild()`、`startAsyncUpdate()` — 移出 `createPhysicsBody()` 和 `replaceCollisionSnapshot()` |
| `physics/body/PhysicsBodyExtension.kt` | 可选：`activate()` 内联 `world.addCollisionObject()` 避免嵌套提交 |

### 场景 A：startAsyncBuild（首次构建）

**当前代码** ([PhysicsChunkSection.kt#L185-L200](file:///d:/Files/Project/MinecraftMods/Spark-Core/src/main/kotlin/cn/solarmoon/spark_core/physics/terrain/PhysicsChunkSection.kt#L185-L200))：

```kotlin
fun startAsyncBuild(manager: PhysicsChunkManager) {
    buildJob?.cancel()
    snapshotForBuild = SectionSnapshot.snapshotFromChunk(physicsLevel.mcLevel, chunk, sectionPos)
    buildJob = manager.terrainBuilderScope.launch {
        val buildResult = buildCollisionShapeAsync(this).await()
        if (buildResult) {
            physicsLevel.submitImmediateTask {        // ← 物理线程
                createPhysicsBody()                    // ← 实际不需要
            }
        }
    }
}
```

**注意**：`createPhysicsBody()` 内部已调用 `replaceCollisionSnapshot(snapshotForBuild)`（见 L255），不需要外部再调用一次。见下文"实施注意事项"。

**改为**：

```kotlin
fun startAsyncBuild(manager: PhysicsChunkManager) {
    buildJob?.cancel()
    snapshotForBuild = SectionSnapshot.snapshotFromChunk(physicsLevel.mcLevel, chunk, sectionPos)
    buildJob = manager.terrainBuilderScope.launch {
        val buildResult = buildCollisionShapeAsync(this).await()
        if (buildResult) {
            createPhysicsBody()                       // ← 直接在构建线程完成，内部含 replaceCollisionSnapshot
            // 刚体不加入世界，等待 updateActivation 激活
        }
    }
}
```

### 场景 B：startAsyncUpdate（脏区重建）

**当前代码** ([PhysicsChunkSection.kt#L205-L248](file:///d:/Files/Project/MinecraftMods/Spark-Core/src/main/kotlin/cn/solarmoon/spark_core/physics/terrain/PhysicsChunkSection.kt#L205-L248))：

```kotlin
fun startAsyncUpdate(manager: PhysicsChunkManager) {
    buildJob?.cancel()
    snapshotForBuild = SectionSnapshot.snapshotFromChunk(physicsLevel.mcLevel, chunk, sectionPos)
    buildJob = manager.terrainBuilderScope.launch {
        val wasActive = isActive
        val hadBody = physicsBody != null
        val hasCollisionNow = buildCollisionShapeAsync(this).await()
        if (hasCollisionNow) {
            if (hadBody) {
                physicsBody?.let { body ->
                    collisionShape?.let { newShape ->
                        physicsLevel.submitImmediateTask(PPhase.PRE) {  // ← 物理线程
                            replaceCollisionSnapshot(snapshotForBuild)   // ← 纯Java
                            body.collisionShape = newShape               // ← 需要物理线程 ✓
                        }
                    }
                }
            } else {
                physicsLevel.submitImmediateTask {    // ← 物理线程
                    createPhysicsBody()                // ← 纯Java
                    if (wasActive) activate()          // → 又嵌套 submitImmediateTask
                }
            }
        }
    }
}
```

**改为**：

```kotlin
fun startAsyncUpdate(manager: PhysicsChunkManager) {
    buildJob?.cancel()
    snapshotForBuild = SectionSnapshot.snapshotFromChunk(physicsLevel.mcLevel, chunk, sectionPos)
    buildJob = manager.terrainBuilderScope.launch {
        val wasActive = isActive
        val hadBody = physicsBody != null
        val hasCollisionNow = buildCollisionShapeAsync(this).await()
        if (hasCollisionNow) {
            if (hadBody) {
                replaceCollisionSnapshot(snapshotForBuild)  // ← 构建线程完成
                val newShape = collisionShape!!
                physicsLevel.submitImmediateTask(PPhase.PRE) {  // 仅此一步在物理线程
                    physicsBody!!.collisionShape = newShape
                }
            } else {
                createPhysicsBody()                       // ← 构建线程完成，内部含 replaceCollisionSnapshot
                if (wasActive) {
                    val body = physicsBody!!
                    physicsLevel.submitImmediateTask {    // 仅此一步在物理线程
                        physicsLevel.world.addCollisionObject(body)
                    }
                }
            }
            // 注意：else 分支（!hasCollisionNow && hadBody → destroyPhysicsBody()）保持不变，
            // 因为 destroyPhysicsBody() 内部调用了 removePhysicsBody() 需要在物理线程执行。
        }
    }
}
```

### 场景 C：activate() 嵌套提交修复

**当前 `activate()`** ([PhysicsChunkSection.kt#L297-L310](file:///d:/Files/Project/MinecraftMods/Spark-Core/src/main/kotlin/cn/solarmoon/spark_core/physics/terrain/PhysicsChunkSection.kt#L297-L310)) 通过 `addPhysicsBody()` → 又走一次 `submitImmediateTask`：

```kotlin
fun activate() {
    ...
    physicsLevel.mcLevel.addPhysicsBody(physicsBody!!)  // → submitImmediateTask { world.addCollisionObject }
}

// PhysicsBodyExtension.kt
fun Level.addPhysicsBody(body: PhysicsCollisionObject) {
    physicsLevel.submitImmediateTask {
        world.addCollisionObject(body)
    }
}
```

**问题**：`activate()` 在 `updateActivation()` 中被主线程调用 → `addPhysicsBody()` → `submitImmediateTask`。如果 `activate()` 在物理线程中被调用（如 `startAsyncUpdate` 的成功路径），则又多嵌套一层。

**方案**：`PhysicsChunkSection.activate()` 的调用场景已在主线程和物理线程均有，仅调用 `addPhysicsBody()` 即可，由 `addPhysicsBody` 统一处理线程提交。

**可选优化**：提供一个 `activateImmediately()` 方法用于物理线程内直接调用 `world.addCollisionObject()`，消除嵌套提交。改动小，可在后续迭代做。

## 效果预估

### 物理线程负担对比

| 操作 | 优化前（物理线程执行） | 优化后（物理线程执行） |
|------|----------------------|----------------------|
| `createPhysicsBody()` | ✓ native 分配 + 字段设置 | — 移至构建线程 |
| `replaceCollisionSnapshot()` | ✓ 数组复制 | — 移至构建线程 |
| `body.collisionShape = newShape` | ✓ | ✓ |
| `world.addCollisionObject()` | ✓ | ✓ |

以 216 个 section 为例：
- 优化前：432 次物理线程任务（创建 + 加入）
- 优化后：216 次物理线程任务（仅加入世界）
- 物理线程 `processTasks` 耗时减少约 40-50%

### 副作用

- `physicsBody` 字段现在被构建线程写入。当前 `isActive` 已是 `@Volatile`，`physicsBody` 和 `collisionShape` 也需要加 `@Volatile` 或确认现有的 happens-before 关系（通过 `submitImmediateTask` 的队列保证）
- `snapshotForCollision` 现在被构建线程写入（`replaceCollisionSnapshot`），之前只在物理线程写入

### 线程安全审核

| 字段 | 写入线程 | 读取线程 | 状态 |
|------|---------|---------|------|
| `physicsBody` | 构建线程（createPhysicsBody） | 物理线程、主线程（activate/deactivate 检查 null） | 需要加 `@Volatile` |
| `snapshotForCollision` | 构建线程（replaceCollisionSnapshot） | 物理线程（碰撞查询） | 需要加 `@Volatile` |
| `collisionShape` | 构建线程（buildCollisionShape 写入，createPhysicsBody 读取） | 物理线程（`isEmpty()` → 碰撞查询路径） | 当前无 `@Volatile`；构建线程内读写在同一个协程中顺序执行，program order 保证可见性；物理线程读取发生在刚体加入世界之后（happens-before 由任务队列保证）。建议加 `@Volatile` 作防御 |
| `isActive` | 物理线程（activate/deactivate 通过 submitImmediateTask） | 主线程（updateActivation 守卫条件、activateSectionsInRanges 遍历） | **预存问题**：当前无 `@Volatile`，主线程可能读到过期值。非本优化引入，但应记录，建议后续加 `@Volatile` |

## 实施步骤

1. **`createPhysicsBody()` 拆分为两段**
   - 纯 Java 部分：`new PhysicsRigidBody()` + 属性设置 — 在构建线程执行
   - 加入世界：`world.addCollisionObject()` — 通过 `submitImmediateTask` 投递到物理线程

2. **修改 `startAsyncBuild()`**
   - 构建线程完成 `createPhysicsBody()` + `replaceCollisionSnapshot()`
   - 不再投递 `submitImmediateTask`

3. **修改 `startAsyncUpdate()`**
   - 已有刚体路径：构建线程完成 `replaceCollisionSnapshot()`，仅投递 `body.collisionShape = newShape`
   - 新建刚体路径：构建线程完成 `createPhysicsBody()` + `replaceCollisionSnapshot()`，仅投递 `world.addCollisionObject()`

4. **添加 `@Volatile` 标注**
   - `physicsBody`、`snapshotForCollision`

5. **（可选）修复 `activate()` 嵌套提交**
   - 在物理线程内的调用路径直接执行 `world.addCollisionObject()`，避免二次入队

## 实施注意事项

### `createPhysicsBody()` 内部已含 `replaceCollisionSnapshot()`

[createPhysicsBody() L255](file:///d:/Files/Project/MinecraftMods/Spark-Core/src/main/kotlin/cn/solarmoon/spark_core/physics/terrain/PhysicsChunkSection.kt#L255) 已调用 `replaceCollisionSnapshot(snapshotForBuild)`，外部调用处无需再显式调用，否则会重复复制 4096 长度的数组。如果未来需要拆分此方法（例如分离刚体创建和快照替换），可改为：

```kotlin
// 内部方法：仅创建刚体，不替换快照
private fun createPhysicsBodyInternal(): Boolean {
    val shape = collisionShape ?: return false
    physicsBody = PhysicsRigidBody(shape, 0f).apply { ... }
    return true
}
```

### `destroyPhysicsBody()` 路径保持原样

`startAsyncUpdate` 的 `!hasCollisionNow && hadBody` 分支调用 `destroyPhysicsBody()`，其内部需要 `world.removeCollisionObject()`（物理线程操作），当前已在 `submitImmediateTask` 内执行，无需改动。

## 风险评估

- **低风险**：改动集中在 `PhysicsChunkSection` 两个方法内，不影响外部接口
- **线程安全**：核心字段已有 `@Volatile` 保护或通过任务队列的 happens-before 保证
- **功能等价**：构建线程和物理线程的先后顺序由 `submitImmediateTask` 队列保证，不改变语义
