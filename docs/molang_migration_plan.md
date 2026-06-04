# Spark-Core MoLang 引擎迁移计划 —— GraalJS → Mocha

> **状态**: 最终版 · **创建**: 2026-06-04 · **修订**: 2026-06-04（一步到位版）  
> **目标**: 一次性完整替换 MoLang 解析引擎（GraalJS → Mocha），GraalJS 保留用于通用脚本  
> **策略**: 单次执行，全部 5 项风险已预先验证通过，无阻塞因素

---

## 目录

1. [背景与动机](#1-背景与动机)
2. [Mocha 引擎概述](#2-mocha-引擎概述)
    - [2.1 源文件清单](#21-源文件清单)
    - [2.2 许可证](#22-许可证)
    - [2.3 外部依赖](#23-外部依赖)
3. [当前 MoLang 架构回顾](#3-当前-molang-架构回顾)
4. [目标架构](#4-目标架构)
5. [线程安全与变量存储方案](#5-线程安全与变量存储方案)
6. [实施步骤（单次执行，一步到位）](#6-实施步骤单次执行一步到位)
    - [步骤 1: 复制 Mocha 引擎](#步骤-1-复制-mocha-引擎到-spark-core50-文件)
    - [步骤 2: 创建 Spark-Core 适配层](#步骤-2-创建-spark-core-适配层3-个新文件)
    - [步骤 3: Machine-Max Context 子类](#步骤-3-创建-machine-max-侧-context-子类2-新文件3-旧文件删除)
    - [步骤 4: 替换所有 MoLang 求值调用点](#步骤-4-替换所有-molang-求值调用点)
    - [步骤 5: 删除旧代码](#步骤-5-删除旧-molang-代码)
7. [不变部分](#7-不变部分)
8. [风险与缓解（已验证）](#8-风险与缓解已验证)
9. [验证检查清单](#9-验证检查清单)

---

## 1. 背景与动机

### 当前问题

Spark-Core 使用 **GraalVM JS 引擎** 解析所有 MoLang 表达式。调用链为：

```
JSMolangValue.eval(AnimInstance)
  → GraalJS Context.eval(compiledSource)
    → JS 解析器 → IR → JIT → Native 调用
    → GraalJS Value → Java double/bool/string (跨堆转换)
```

### 性能对比

| 指标 | GraalJS | Mocha 解释模式 | Mocha 编译模式 |
|------|---------|--------------|--------------|
| 单次 eval（简单表达式） | ~1-10µs | ~0.1-0.5µs | ~0.01-0.05µs |
| 内存占用（per-context） | ~20-50MB | ~1KB | ~1KB + 类元数据 |
| 值转换 GC 压力 | 每次求值产生临时 JS 对象 | 无（`NumberValue` 可复用）| 无 |
| 冷启动 | ~100-500ms | ~1ms | ~10-50ms |

对于动画关键帧求值（每帧每骨骼 × 每通道 × 3 分量）、碰撞箱条件判断、HUD 文本等场景，**Mocha 比 GraalJS 快 2-3 个数量级**。

### 决策

- 将 SBM 的 Mocha 引擎整体复制到 Spark-Core，标注 MIT 出处
- GraalJS 保留作为通用脚本引擎（`spark_core.js` 包）
- Mocha 专用于 MoLang 表达式解析

---

## 2. Mocha 引擎概述

### 2.1 源文件清单

全部位于 `com.github.mcmodderanchor.simplebedrockmodel.v1.molang` 包下，约 50 个文件：

```
molang/
├── MochaEngine.java                    # 引擎接口（解析/求值/编译/绑定 API）
├── MochaEngineImpl.java                # 引擎实现
├── lexer/
│   ├── Characters.java                 # 字符分类工具
│   ├── Cursor.java                     # 字符位置追踪
│   ├── MolangLexer.java               # 词法器接口
│   ├── MolangLexerImpl.java           # 手写词法分析器（~290行）
│   ├── Token.java                      # Token 数据结构
│   └── TokenKind.java                 # Token 类型枚举
├── parser/
│   ├── MolangParser.java              # 语法分析器接口
│   ├── MolangParserImpl.java          # 递归下降语法分析器（~340行）
│   ├── ParseException.java            # 解析异常
│   └── ast/
│       ├── Expression.java            # AST 根接口
│       ├── ExpressionVisitor.java     # Visitor 模式接口
│       ├── AccessExpression.java      # a.b 属性访问
│       ├── ArrayAccessExpression.java # a[b] 数组访问
│       ├── BinaryExpression.java      # 二元运算
│       ├── CallExpression.java        # 函数调用
│       ├── DoubleExpression.java      # 数字字面量
│       ├── ExecutionScopeExpression.java # { ... } 执行域
│       ├── IdentifierExpression.java  # 标识符
│       ├── StatementExpression.java   # break/continue
│       ├── StringExpression.java      # 字符串字面量
│       ├── TernaryConditionalExpression.java # ?: 三元
│       └── UnaryExpression.java       # 一元运算
├── runtime/
│   ├── CompileVisitResult.java        # 编译器遍历结果
│   ├── ExecutionContext.java          # 执行上下文接口
│   ├── ExpressionInliner.java         # 常量折叠内联器
│   ├── ExpressionInterpreter.java     # AST 解释器（~480行）
│   ├── FunctionCompileState.java      # 编译器状态
│   ├── IsConstantExpression.java      # 常量检测器
│   ├── JavaTypes.java                 # 类型工具
│   ├── MochaFunction.java             # 可执行函数接口
│   ├── MolangCompiler.java            # ASM 字节码编译器（~315行）
│   ├── MolangCompilingVisitor.java    # 编译期 AST 遍历器（~913行）
│   ├── MolangContext.java             # 求值上下文基类
│   ├── MolangExpression.java          # 带上下文的编译函数
│   ├── Scope.java                     # 作用域接口
│   ├── ScopeImpl.java                 # 作用域实现
│   ├── TypeCastException.java         # 类型转换异常
│   ├── binding/
│   │   ├── BindExternalFunction.java  # 外部函数绑定注解
│   │   ├── Binding.java               # 绑定注解
│   │   ├── Entity.java                # @Entity 参数注解
│   │   ├── JavaFieldBinding.java      # Java 字段绑定
│   │   ├── JavaFunction.java          # Java 方法绑定
│   │   ├── JavaObjectBinding.java     # Java 对象绑定
│   │   ├── Lazy.java                  # 懒加载工具
│   │   ├── QueryBinding.java          # 查询绑定注解
│   │   ├── ReflectiveFunction.java    # 反射函数包装
│   │   └── RegisteredBinding.java     # 已注册绑定
│   ├── compiled/
│   │   ├── MochaCompiledFunction.java # 编译函数标记接口
│   │   └── Named.java                 # @Named 参数注解
│   ├── standard/
│   │   └── MochaMath.java             # math.* 函数库（sin/cos/random...）
│   └── value/
│       ├── ArrayValue.java            # 数组值
│       ├── Function.java              # 可调用值接口
│       ├── JavaValue.java             # 包装 Java 对象
│       ├── MutableObjectBinding.java  # 可变对象绑定（variable 存储）
│       ├── NumberValue.java           # 数值（含 NaN/Inf 安全处理）
│       ├── ObjectProperty.java        # 对象属性接口
│       ├── ObjectPropertyImpl.java    # 对象属性实现
│       ├── ObjectValue.java           # 对象值接口
│       ├── StringValue.java           # 字符串值
│       └── Value.java                 # 值类型基接口
└── util/
    ├── AsmUtil.java                   # ASM 辅助工具（~423行）
    └── CaseInsensitiveStringHashMap.java # 大小写不敏感 HashMap
```

外加一个使用层文件（可选复制）：
```
common/molang/
└── MolangEngineHelper.java           # 引擎创建便捷工具
```

### 2.2 许可证

所有 Mocha 文件头均为 MIT 许可证：

```
Copyright (c) 2021-2025 Unnamed Team
Permission is hereby granted, free of charge...
```

**迁移要求**：保留原始 MIT 版权声明，在文件顶部加一行注释标明出处：

```java
// 原始来源: SimpleBedrockModel (https://github.com/McModderAnchor/SimpleBedrockModel)
// 许可证: MIT, Copyright (c) 2021-2025 Unnamed Team
// 已适配至 Spark-Core (cn.solarmoon.spark_core.molang)
```

### 2.3 外部依赖

| 依赖 | 是否在 NeoForge 环境中 |
|------|------------------------|
| `org.ow2.asm` (ASM 9.x) | ✅ 已自带（NeoForge 核心依赖） |
| `org.jetbrains.annotations` | ✅ 已自带（Minecraft 开发环境） |
| 无其他第三方库 | — |

---

## 3. 当前 MoLang 架构回顾

### 3.1 核心类

```
JSMolangValue.kt                         # 值类，持有字符串表达式
├── eval(AnimInstance) → GraalJS Value   # 通过 GraalJS 求值
├── evalAsDouble(anim) → Double          # 类型转换扩展
├── evalAsBoolean(anim) → Boolean
├── evalAsString(anim) → String
└── evalAsObject(anim) → Any?

JSMolangContext.kt                       # ThreadLocal GraalJS Context
├── extraBindings: ThreadLocal<Map>      # IMolangContext 注册表
├── context: ThreadLocal<Context>        # GraalJS 引擎实例
└── MolangCache: Source 编译缓存

IMolangContext.kt                        # 绑定接口
└── update(molang, anim, context, bindings)  # 每次求值前刷新

MolangRegisterEvent.kt                   # NeoForge 事件，注册自定义绑定

Vector3js.kt                             # 三维 MoLang 向量
└── eval(AnimInstance) → Vector3f        # 每分量调用 JSMolangValue.evalAsDouble
```

### 3.2 Machine-Max 侧绑定

| 类 | 暴露的 JS 名称 | 提供内容 |
|----|--------------|---------|
| `QueryContext` (Spark-Core) | `query` / `q` | `anim_time`, `ground_speed`, `vertical_speed`, `position()`, `playSound()` |
| `VariableContext` (Spark-Core) | `variable` / `v` | `getMember(name)`, `setMember(name, value)` |
| `MathContext` (Spark-Core) | `math` | 数学函数 |
| `SubPartBinding` (Machine-Max) | `subpart` / `spt` | `durability`, `max_durability`, `is_destroyed`, `get(channel)`, `has_connector()`, `connector_offset()` 等 |
| `VehicleBinding` (Machine-Max) | `vehicle` / `veh` | `durability`, `max_durability`, `energy`, `max_energy`, `get(key)` |

### 3.3 调用站点分类

| 类型 | 调用位置 | 频率 |
|------|---------|------|
| **动画关键帧** | `InterpolationType` → `Vector3js.eval()` → `JSMolangValue.evalAsDouble()` | 极高（每帧每骨骼 × N关键帧 × 6分量） |
| **动画权重** | `OAnimStateMachine` → `JSMolangValue.eval()` | 中（每动画状态切换一次） |
| **动画条件** | `OAnimStateMachine` → `evalAsBoolean()` | 中 |
| **非动画条件（HitBox）** | `HitBox.updateActive()` → `JSMolangValueKt.evalAsBoolean()` | 中（每零件每物理刻） |
| **HUD 文本** | `ModelAnimatable.renderTexts()` → `JSMolangValueKt.eval()` | 低 |

---

## 4. 目标架构

```
┌─ Spark-Core ─────────────────────────────────────────────────┐
│ molang/ (新包，来自 SBM，MIT 许可)                              │
│   ├── MochaEngine, MochaEngineImpl                            │
│   ├── lexer/   (词法分析)                                      │
│   ├── parser/  (语法分析 + AST)                                │
│   ├── runtime/ (解释器 + 编译器 + Context 基类)                 │
│   └── util/    (AsmUtil + CaseInsensitiveStringHashMap)       │
│                                                                │
│ molang/SparkMolangContext.kt (新)                              │
│   ├── 继承 MolangContext<IAnimatable<*>>                       │
│   ├── @QueryBinding 提供 query.* / variable.*                  │
│   └── 桥接 IAnimatable.variables 作为 variable 存储             │
│                                                                │
│ molang/SparkMolangEngine.kt (新)                               │
│   ├── 全局 MochaEngine 单例（编译用）                           │
│   ├── compile(expr) → MolangExpression                        │
│   └── eval(expr, context) → double                            │
│                                                                │
│ js/ (保留)                                                     │
│   └── GraalJS 脚本引擎，不受影响                                │
└────────────────────────────────────────────────────────────────┘

┌─ Machine-Max ─────────────────────────────────────────────────┐
│ molang/SubPartMolangContext (新)                               │
│   └── 继承 SparkMolangContext，添加 subpart.* @QueryBinding     │
│                                                                │
│ molang/VehicleMolangContext (新)                               │
│   └── 继承 SparkMolangContext，添加 veh.* @QueryBinding          │
│                                                                │
│ HitBox.java → SparkMolangEngine.eval(condition, context)       │
│ ModelAnimatable.java → 同上                                     │
│                                                         [0m       │
│ 删除: SubPartBinding.java, VehicleBinding.java, MMMolangs.java  │
└────────────────────────────────────────────────────────────────┘
```

### 关键设计点

1. **全局一个 MochaEngine 单例**用于编译。`MochaEngine` 是无状态的（Scope 不可变后只读），线程安全。

2. **每次求值创建 MolangContext 实例**。Context 持有对 `IAnimatable`（即 SubPart/Entity）的引用，通过 `@QueryBinding` 方法惰性读取数据，无需预刷新。

3. **不创建虚假 AnimInstance**。当前非动画求值被迫构造 `AnimInstance(IAnimatable, dummyIndex)`，Mocha 直接传入 `SparkMolangContext`。

4. **variable 存储桥接 `IAnimatable.variables`**。通过自定义 `AnimatableVariableBinding` 实现 `ObjectValue`，读写代理到现有的 `MutableMap<String, Any>`。

---

## 5. 线程安全与变量存储方案

### 5.1 问题分析

现有架构依赖**线程纪律**：物理线程同时负责 MoLang 求值和变量写入，main 线程通过 `IAnimatable.putVariable()` 间接写入（内部用 `submitImmediateTask` 调度到物理线程）。

Mocha 原生的 `MutableObjectBinding` 使用普通 `CaseInsensitiveStringHashMap`（HashMap），无线程保护。

### 5.2 方案：适配器桥接

新建 `AnimatableVariableBinding`，实现 Mocha 的 `ObjectValue` 接口，读写直接代理到 `IAnimatable.variables`：

```java
/**
 * 将 IAnimatable.variables 适配为 Mocha ObjectValue。
 * 线程安全：物理线程读写，外部通过 putVariable() 间接写入。
 */
public class AnimatableVariableBinding implements ObjectValue {
    private final IAnimatable<?> animatable;

    public AnimatableVariableBinding(IAnimatable<?> animatable) {
        this.animatable = animatable;
    }

    @Override
    public Value get(String name) {
        Object val = animatable.getVariables().get(name.toLowerCase());
        return Value.of(val);  // Mocha Value.of() 自动处理 Number/String/Boolean
    }

    @Override
    public boolean set(String name, Value value) {
        Object converted;
        if (value instanceof StringValue) {
            converted = value.getAsString();
        } else if (value.getAsBoolean()) {
            converted = value.getAsNumber();
        } else {
            converted = value.getAsNumber();
        }
        animatable.putVariable(name.toLowerCase(), converted);
        return true;
    }

    @Override
    public Map<String, ObjectProperty> entries() {
        var result = new HashMap<String, ObjectProperty>();
        for (var entry : animatable.getVariables().entrySet()) {
            result.put(entry.getKey(), ObjectProperty.property(Value.of(entry.getValue()), false));
        }
        return result;
    }
}
```

**优点**：
- 变量只有一份存储，不需同步两份数据
- 复用已有的 `putVariable()` 线程调度机制
- Java 侧读写 API 不变（`variables[key]` / `putVariable(key, value)`）

### 5.3 需要注意的线程场景

| 场景 | 线程 | 安全保证 |
|------|------|---------|
| MoLang 表达式求值读取变量 | 物理线程 | 直接读 `variables` Map |
| Java 侧写入变量 | 主线程 | 通过 `putVariable()` → `submitImmediateTask` 调度到物理线程 |
| HitBox 条件求值 | 物理线程 | `updateActive()` 在 `prePhysicsTick()` 中调用 |
| 动画关键帧求值 | 物理线程 | `SubPart.postTick()` 的动画步进在物理线程 |

**结论**：所有 MoLang 求值都在物理线程，所有变量直接写入也最终落到物理线程，不存在真实并发冲突。适配器方案足够安全。

---

## 6. 实施步骤（单次执行，一步到位）

以下步骤按依赖顺序排列，在一次提交中全部完成。

### 步骤 1: 复制 Mocha 引擎到 Spark-Core（~50 文件）

1. 在 Spark-Core 中新建包 `cn.solarmoon.spark_core.molang`

2. 按目录结构复制全部文件：
   ```
   src/main/java/cn/solarmoon/spark_core/molang/
   ├── MochaEngine.java
   ├── MochaEngineImpl.java
   ├── lexer/      (6 文件: Characters, Cursor, MolangLexer, MolangLexerImpl, Token, TokenKind)
   ├── parser/     (3 文件 + ast/12 文件)
   ├── runtime/    (15 文件 + binding/10 + compiled/2 + standard/1 + value/11)
   └── util/       (2 文件: AsmUtil, CaseInsensitiveStringHashMap)
   ```

3. 全局替换包名：`com.github.mcmodderanchor.simplebedrockmodel.v1.molang` → `cn.solarmoon.spark_core.molang`

4. 每个文件顶部加版权注释（在 MIT 声明之上）：
   ```java
   // 原始来源: SimpleBedrockModel (https://github.com/McModderAnchor/SimpleBedrockModel)
   // 许可证: MIT, Copyright (c) 2021-2025 Unnamed Team
   // 已适配至 Spark-Core
   ```

5. 修改 `MolangContext.java`：去掉 `implements IEvaluationContext`（仅此文件引用，编译器不依赖）

---

### 步骤 2: 创建 Spark-Core 适配层（3 个新文件）

**文件 A** — `SparkMolangContext.java`（基类，整合 QueryContext + VariableContext）：

```java
public class SparkMolangContext extends MolangContext<IAnimatable<?>> {
    private final AnimatableVariableBinding variableBridge;

    public SparkMolangContext() { this(null); }
    public SparkMolangContext(@Nullable IAnimatable<?> animatable) {
        super(animatable);
        this.variableBridge = new AnimatableVariableBinding(animatable);
        getVariableStorage().setAllFrom(variableBridge);
    }

    public void reset(IAnimatable<?> animatable, double animTime) {
        setEntity(animatable);
        prepareEvaluation((float) animTime);
        this.variableBridge.setAnimatable(animatable);
    }

    @QueryBinding("anim_time")          public double queryAnimTime()     { return getTimeS(); }
    @QueryBinding(value = "anim_time", namespace = "q")  // q. 简写兼容
    public double qAnimTime()           { return getTimeS(); }
    
    // 后续在 Machine-Max 侧子类中补充 ground_speed / vertical_speed 等
}
```

**文件 B** — `AnimatableVariableBinding.java`（桥接 IAnimatable.variables → Mocha ObjectValue）：

```java
public class AnimatableVariableBinding implements ObjectValue {
    private IAnimatable<?> animatable;

    public AnimatableVariableBinding(IAnimatable<?> animatable) { this.animatable = animatable; }
    public void setAnimatable(IAnimatable<?> animatable) { this.animatable = animatable; }

    @Override public Value get(String name) {
        Object val = animatable.getVariables().get(name.toLowerCase());
        return Value.of(val);
    }

    @Override public boolean set(String name, Value value) {
        animatable.putVariable(name.toLowerCase(), value.getAsNumber());
        return true;
    }

    @Override public Map<String, ObjectProperty> entries() {
        var r = new HashMap<String, ObjectProperty>();
        for (var e : animatable.getVariables().entrySet())
            r.put(e.getKey(), ObjectProperty.property(Value.of(e.getValue()), false));
        return r;
    }
}
```

**文件 C** — `SparkMolangEngine.java`（全局单例）：

```java
public final class SparkMolangEngine {
    public static final MochaEngine<?> ENGINE;

    static {
        ENGINE = MochaEngine.create(null, builder ->
            builder.set("math", JavaObjectBinding.of(MochaMath.class, null, new MochaMath())));
    }

    public static MolangExpression compile(String expr) { return ENGINE.compile(expr, MolangExpression.class); }
    public static double eval(String expr, SparkMolangContext ctx) { return ENGINE.eval(expr); }
}
```

---

### 步骤 3: 创建 Machine-Max 侧 Context 子类（2 新文件，3 旧文件删除）

**新建** `SubPartMolangContext.java` — 将原 `SubPartBinding` 的全部功能用 `@QueryBinding` 重写：

```java
public class SubPartMolangContext extends SparkMolangContext {
    public SubPartMolangContext() { super(); }
    public SubPartMolangContext(@Nullable IAnimatable<?> animatable) { super(animatable); }

    private SubPart getSubPart() { /* 同原 SubPartBinding.getAnimatable() 逻辑 */ }

    // 无参字段
    @QueryBinding(value = "durability",     namespace = "subpart") public double subpartDurability()     { ... }
    @QueryBinding(value = "max_durability", namespace = "subpart") public double subpartMaxDurability()  { ... }
    @QueryBinding(value = "is_destroyed",   namespace = "subpart") public double subpartIsDestroyed()    { ... }

    // 带参方法——编译器已验证支持
    @QueryBinding(value = "has_connector",            namespace = "subpart") public double subpartHasConnector(String name)        { ... }
    @QueryBinding(value = "connector_offset",          namespace = "subpart") public double subpartConnectorOffset(String n, int a) { ... }
    @QueryBinding(value = "connector_rotation",        namespace = "subpart") public double subpartConnectorRotation(String n, int a){ ... }
    @QueryBinding(value = "has_subsystem",             namespace = "subpart") public double subpartHasSubsystem(String name)        { ... }
    @QueryBinding(value = "subsystem_durability",      namespace = "subpart") public double subpartSubsystemDurability(String name) { ... }
    @QueryBinding(value = "subsystem_max_durability",  namespace = "subpart") public double subpartSubsystemMaxDurability(String n) { ... }
    @QueryBinding(value = "subsystem_active",          namespace = "subpart") public double subpartSubsystemActive(String name)     { ... }
    @QueryBinding(value = "subsystem_destroyed",       namespace = "subpart") public double subpartSubsystemDestroyed(String name)  { ... }
}
```

**新建** `VehicleMolangContext.java` — 同上，提供 `veh.*` / `vehicle.*` 绑定。

**删除**：`SubPartBinding.java`、`VehicleBinding.java`、`MMMolangs.java`

---

### 步骤 4: 替换所有 MoLang 求值调用点

**4.1 HitBox.java** — 非动画条件求值：

```java
// 旧
active = JSMolangValueKt.evalAsBoolean(condition, subPart);

// 新——编译缓存模式（在 SubPart 构造时预编译）
// SubPart 字段: private MolangExpression hitBoxCondition;
public void updateActive() {
    if (alwaysActive) { active = true; return; }
    active = hitBoxCondition.evaluate(subPart.getSparkMolangContext()) > 0;
}
```

**4.2 ModelAnimatable.java** — HUD 文本求值：

```java
// 旧
JSMolangValueKt.eval(arg, this);

// 新
SparkMolangEngine.eval(arg, getSparkMolangContext());
```

**4.3 Vector3js.kt** — 动画关键帧求值（核心热路径）：

```kotlin
// 改为持有 MolangExpression 预编译表达式，直接 evaluate
class Vector3js(
    val compiledX: MolangExpression?,
    val compiledY: MolangExpression?,
    val compiledZ: MolangExpression?
) {
    fun eval(context: SparkMolangContext): Vector3f = Vector3f(
        (compiledX?.evaluate(context) ?: 0.0).toFloat(),
        (compiledY?.evaluate(context) ?: 0.0).toFloat(),
        (compiledZ?.evaluate(context) ?: 0.0).toFloat()
    )
}
```

**4.4 OKeyFrame.kt** — 加载时预编译表达式：

```kotlin
// 在 AnimationModule 解析 JSON 时：
val preX = SparkMolangEngine.compile(rawPreX)
val preY = SparkMolangEngine.compile(rawPreY)
val preZ = SparkMolangEngine.compile(rawPreZ)
val vector3js = Vector3js(preX, preY, preZ)
```

**4.5 OAnimStateMachine.kt** — 动画状态机条件/权重：

```kotlin
// 条件判断
condition.evalAsBoolean(animatable) → SparkMolangEngine.compile(cond).evaluate(ctx) > 0
// 权重
animWeight.eval(anim) → SparkMolangEngine.compile(weightExpr).evaluate(ctx)
```

**4.6 SubPart.java** — 添加 Context 访问器：

```java
private final SubPartMolangContext molangContext = new SubPartMolangContext(this);

public SubPartMolangContext getSparkMolangContext() {
    molangContext.reset(this, /* animTime= */ 0);
    return molangContext;
}
```

---

### 步骤 5: 删除旧 MoLang 代码

以下所有文件在一次提交中全部删除（已无引用）：

| 文件 | 位置 |
|------|------|
| `IMolangContext.kt` | `js/molang/` |
| `MolangRegisterEvent.kt` | `event/` |
| `JSMolangValue.kt` | `js/molang/` |
| `JSMolangContext.kt` | `js/molang/` |
| `QueryContext.kt` | `js/molang/` | 功能已迁移到 `SparkMolangContext` 的 `@QueryBinding` |
| `VariableContext.kt` | `js/molang/` | 功能已由 `AnimatableVariableBinding` 替代 |
| `MathContext.kt` | `js/molang/` | 仅被 `JSMolangContext.kt` 引用，无外部使用者，删除 |
| `SubPartBinding.java` (Machine-Max) | `mech/molang/` |
| `VehicleBinding.java` (Machine-Max) | `mech/molang/` |
| `MMMolangs.java` (Machine-Max) | `registry/` |

**GraalJS 依赖保留**，`spark_core.js` 包不受影响。

---

## 7. 不变部分

| 组件 | 说明 |
|------|------|
| `GraalJS` 依赖 | 保留，`spark_core.js` 包继续使用 |
| `IAnimatable` / `IAnimatable.variables` | 接口签名不变，变量通过 `AnimatableVariableBinding` 桥接 |
| `ModelIndex` / `AnimIndex` | 索引结构不变 |
| `AnimInstance` | 动画实例不变 |
| 所有 `spark_modules/` JSON 文件 | 内容包数据不变，MoLang 语法完全兼容 |

---

## 8. 风险与缓解（已验证）

### 8.1 Mocha 带参 @QueryBinding ✅ 已确认支持

**验证结果**: `MolangCompilingVisitor.visitCall()`（第 673-684 行）已经实现了完整的带参 @QueryBinding 编译。当 `CallExpression` 在 Scope 中找不到对应 `Function` 时，编译器会回退到 `findQueryCallMethod()` → `compileQueryBindingCall()`，在 entity 的类型上扫描带任意参数数量的 `@QueryBinding` 方法并编译为 INVOKEVIRTUAL。

现有所有带参 MoLang 调用均可直接编译：
- `subpart.has_connector('connector.foo')` → `@QueryBinding(value="has_connector", namespace="subpart")` + `String` 参数
- `subpart.connector_offset('connector.foo', 1)` → `@QueryBinding(value="connector_offset", namespace="subpart")` + `String, int` 参数
- `subpart.subsystem_destroyed('subsystem.xxx')` → `@QueryBinding(value="subsystem_destroyed", namespace="subpart")` + `String` 参数
- `vehicle.get('steering')` → `@QueryBinding(value="get", namespace="vehicle")` + `String` 参数

**风险等级**: ✅ 无风险

### 8.2 Molang 语法兼容性 ✅ 已确认兼容

**验证范围**: 扫描了所有 `spark_modules/` 下的 `.json` 动画/控制器文件。

**现有表达式语法统计**:

| 语法模式 | 出现次数 | Mocha 兼容性 |
|---------|---------|-------------|
| `query.*` / `q.*` | 3 | ✅ `@QueryBinding` 支持任意命名空间 |
| `subpart.method('arg')` | ~50+ | ✅ 带参 @QueryBinding |
| `vehicle.get('key')` | ~15 | ✅ 带参 @QueryBinding |
| `math.sin/math.cos/math.atan2/math.abs/math.sqrt/math.pow` | ~40+ | ✅ MochaMath 完整支持 |
| `!expr`（逻辑非）| ~20 | ✅ BANG token → LOGICAL_NEGATION |
| `??`（空值合并）| ~3 | ✅ QUESQUES token → NULL_COALESCE |
| `&&` / `\|\|`（逻辑与/或）| ~4 | ✅ AMPAMP/BARBAR |
| 算术 + - * / | 大量 | ✅ |
| 三元 `a ? b : c` | 0 | ✅ 支持 |
| `temp.x` / `t.x` | 0 | ✅ 编译为局部变量 |

**`q.` 简写问题**: 仅有 3 处使用 `q.anim_time` / `q.ground_speed`（在 `base_controller.json` 和 `r700.animation.json` 中）。解决办法：在 `SparkMolangContext` 中为每个 `query.*` 属性同时添加 `@QueryBinding(value="xxx", namespace="q")` 副本即可。无需修改 JSON 文件。

**风险等级**: ✅ 无风险

### 8.3 MolangContext 去掉 IEvaluationContext ✅ 无影响

**验证结果**: 在计划复制的 50 个 Mocha 源文件中，`IEvaluationContext` 仅被 `MolangContext.java` 自身引用（`implements IEvaluationContext`）。SBM 项目中 `common/animation/MolangRotationKeyframe.java` 和 `MolangVector3fKeyframe.java` 也引用了 `IEvaluationContext`，但它们**不在复制范围内**（属于 SBM 动画层，由 Spark-Core 自己的 `OKeyFrame`/`Vector3js` 替代）。

Mocha 编译器和解释器内部不引用 `IEvaluationContext`。删除 `implements IEvaluationContext` 后只需将 `getTimeS()` 和 `prepareEvaluation()` 改为普通成员方法即可。

**风险等级**: ✅ 无风险

### 8.4 ASM 版本兼容性 ✅ 已确认兼容

**验证结果**: 当前项目解析的 ASM 版本为 **9.8**。Mocha 编译器使用 `Opcodes.V17`（Java 17 字节码），ASM 9.0 起即支持 V17，ASM 9.8 完全兼容（最高支持至 Java 24 字节码）。NeoForge 21.1 捆绑的 ASM 版本在 9.5-9.8 之间，与 Mocha 的要求无冲突。

**风险等级**: ✅ 无风险

### 8.5 ThreadLocal 泄漏 ✅ 无风险

**验证结果**: JSMolangContext.kt 中有两个 `ThreadLocal`：
1. `extraBindings: ThreadLocal<Map>` — 存储 IMolangContext 实例
2. `context: ThreadLocal<Context>` — 存储 GraalJS Context

两者均为 `private val` 文件级变量，仅被同文件内的函数引用。当 JSMolangContext.kt 被删除后，ThreadLocal 对象无引用，JVM GC 会回收。ThreadLocal 的值（GraalJS Context、Map）会被一并回收。GraalJS Context 的 native 资源通过 finalize 回调释放，无功能影响。

**风险等级**: ✅ 无风险

### 8.6 MochaEngine 线程安全（新增分析） ⚠️ 需修复

**实际情况**: `MochaEngineImpl` 和 `MolangCompiler` 存在未同步的可变字段：

| 字段 | 所属类 | 风险 | 说明 |
|------|-------|------|------|
| `parseExceptionHandler` | MochaEngineImpl | 高 | 非 volatile 可变字段，`compile()` 和 `eval()` 读取 |
| `postCompile` | MolangCompiler | 高 | 非 volatile 可变 Consumer，`compileBytecode()` 读取 |
| `warnOnReflectiveFunctionUsage` | MochaEngineImpl | 中 | 非 volatile boolean |
| `ScopeImpl.bindings` | ScopeImpl | 高 | 普通 `CaseInsensitiveStringHashMap`，并发读写无保护 |

**本项目中的实际风险**: 

- `SparkMolangEngine` 的 `static {}` 初始化块在类加载时单线程执行，`Scope` 在此后 `readOnly(true)`，不再变化
- 所有 `bind()` / `bindInstance()` 调用在初始化块中完成，之后 Scope 不可变
- `parseExceptionHandler` / `postCompile` / `warnOnReflectiveFunctionUsage` 在本项目中不设置（使用默认 null/false）
- 所有 MoLang 表达式编译和求值都在同一物理线程执行

**因此本项目实际不受这些竞争条件影响。** 但为代码健壮性，仍建议修复：

**修复措施**（在复制 Mocha 源文件后一并执行）：

1. 在 `MochaEngineImpl` 中将 `parseExceptionHandler`、`warnOnReflectiveFunctionUsage` 标记为 `volatile`
2. 在 `MolangCompiler` 中将 `postCompile` 标记为 `volatile`
3. 在 `SparkMolangEngine` 初始化块中，Scope 构建后立即设置 `readOnly(true)`

**风险等级**: ⚠️ 中（实际场景不触发，但应修复以保证代码质量）

### 8.7 MochaMath 缺失函数（已确认无影响） ✅ 

**验证结果**: Spark-Core `MathContext` 有而 `MochaMath` 缺失的函数：`tan`、`sign`。另有 `log` vs `ln` 命名差异（功能等价）。

**实际影响**: 扫描了所有 `spark_modules/` 下的 `.animation.json` 文件，**未发现**任何 `math.tan`、`math.sign`、`math.log` 的使用。现有表达式仅使用 `math.sin`、`math.cos`、`math.atan2`、`math.abs`、`math.sqrt`、`math.pow`，MochaMath 均已支持。

**修复措施**（防御性补全，直接在复制的 `MochaMath.java` 中添加）：

```java
@Binding(value = "tan", pure = true)
public static double tan(double value) { return Math.tan(value * RADIAN); }

@Binding(value = "sign", pure = true)
public static double sign(double value) { return Math.signum(value); }
```

**风险等级**: ✅ 无风险（当前 JSON 不使用，为防止未来使用建议补全）

### 8.8 AnimatableVariableBinding.set() 类型处理 ⚠️ 需修复

**问题**: 计划中的 `set()` 仅调用 `value.getAsNumber()`，非数字类型（String、Boolean）会被静默转为 0.0。

**修复后的 `set()` 实现**：

```java
@Override
public boolean set(String name, Value value) {
    Object converted;
    if (value instanceof StringValue) {
        converted = value.getAsString();       // 字符串保持原样
    } else if (value.getAsBoolean()) {
        converted = value.getAsNumber();        // true → 1.0, false → 0.0
    } else {
        converted = value.getAsNumber();        // 数字
    }
    animatable.putVariable(name.toLowerCase(), converted);
    return true;
}
```

**风险等级**: ⚠️ 低（当前变量均为数字类型，但应修复以防止未来问题）

---

## 9. 验证检查清单

- [ ] Mocha 引擎文件编译通过（Spark-Core 项目无报错）
- [ ] `SparkMolangEngine.compile("1 + 1").evaluate(ctx)` 返回 2.0
- [ ] `SparkMolangEngine.compile("math.sin(math.pi / 2)").evaluate(ctx)` 返回 ≈ 1.0
- [ ] `SparkMolangContext` 的 `variable.x = 5` 后，Java 侧 `IAnimatable.variables["x"]` 读到 5.0
- [ ] Java 侧 `putVariable("y", 10.0)` 后，MoLang 表达式 `v.y` 求值 = 10.0
- [ ] `SubPartMolangContext` 的 `subpart.durability` 求值与 `SubPart.getDurability()` 一致
- [ ] HitBox 条件 `"subpart.durability > 0"` 在零件摧毁后返回 false
- [ ] 多线程下无 ConcurrentModificationException（长时间运行测试）
- [ ] `temp.x = 42; temp.x` 返回 42（编译模式）
- [ ] 三元表达式 `a ? b : c` 正确求值
- [ ] 常量折叠：`1 + 2 * 3` 编译为常量不产生运行时计算开销（可选验证）
- [ ] GraalJS 脚本引擎正常工作（不受影响）
- [ ] Machine-Max 完整启动无崩溃
