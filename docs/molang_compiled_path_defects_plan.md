# MoLang 编译路径缺陷修正计划

> 本文修正 MoLang 引擎里「**编译路径**与**解释路径**给出不同结果，或直接产出非法字节码」这一类缺陷。
> 起因是下游 ARMS-Core 的机娘动画在进入空中姿态时**四肢整体不绘制**，追查到根因在引擎侧；
> 该仓库的接入计划见 `../ARMS-Core/docs/MoLang查询面接入实现计划.md`。
>
> 状态：**A1、A2、A4 已落地**（D4 是实施过程中在下游内容包上追加发现的第 4 条缺陷，见 §4），D3 按 §3 的决定交给下游补绑定消解。

## 0. 摘要

| 编号 | 缺陷 | 触发面 | 本次 |
|------|------|--------|------|
| D1 | `/` 除数为 0 时，解释路径返回 0（Molang 规定），编译路径直接 `DDIV` 得到 `NaN` | 任何含非常量除法的表达式 | **实施**（A1） |
| D2 | 赋值语句的左值是多级访问（`v.a.b = 1;`）时，编译产物是空方法体，`evaluate` 一调即 `VerifyError: Operand stack underflow` | 内容包里 3 处，其中 2 处在被播放的常驻层 `pre_parallel0` 的 timeline 上 | **实施**（A2） |
| D3 | 布尔上下文里的**未绑定** `query.*`（`!q.x`、`q.x && …`）会因类型错配抛 `VerifyError: Bad type on operand stack` | 未绑定查询被当作布尔用时 | 不修引擎：由下游补绑定消解（§3） |
| D4 | 一元负号后紧跟成员函数调用（`-math.clamp(x, 0, 40)`）被解析成「对函数表达式取负再调用」，两条路径都取不到该函数、静默返回 0 | 内容包里 6 处（jump / fly 的手臂旋转） | **实施**（A4） |

D1 是阻塞性的：`NaN` 会顺着骨骼姿态进入渲染矩阵，使该骨骼及其子树整体不绘制。

## 1. D1：除零口径不一致

### 1.1 现象

同一个算式 `query.vertical_speed/math.abs(query.vertical_speed)`（下游内容包 `bmpt_main.animation.json` 的 `jump` 动画里 6 根四肢骨骼的旋转分量）：

| 求值方式 | 结果 |
|----------|------|
| 解释路径（表达式为纯字面量，走编译期常量折叠，如 `1/0`） | `0` |
| 编译路径（表达式含 `query.*` 等非常量子项，运行时求值） | `NaN` |

实测（在下游仓库的 JUnit 车道里搭一个 `SparkMolangContext` 原型，逐条编译求值）：

```
query.vertical_speed                      -> 0.0      # 未绑定，静默取 0
math.abs(query.vertical_speed)            -> 0.0
query.vertical_speed/math.abs(...)        -> NaN      # 真凶
query.vertical_speed/0                    -> NaN
1/0                                       -> 0.0      # 常量折叠走解释器
-30+(query.vertical_speed/math.abs(query.vertical_speed)*(1-math.exp(-0.1*math.abs(query.vertical_speed)))*30)
                                          -> NaN      # jump:LeftArm 的原式
```

`NaN` 之后的链路没有任何有限性检查：`../Spark-Core/src/main/java/cn/solarmoon/spark_core/js/molang/Vector3js.kt#eval` 把它写进 `Vector3f`，`../Spark-Core/src/main/java/cn/solarmoon/spark_core/animation/anim/origin/InterpolationType.kt#lerp` 用 `Mth.lerp` / `Mth.catmullrom` 原样传播，最终进入骨骼矩阵。`../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/runtime/value/NumberValue.java#normalize` 会把 `NaN` 折成 0，但它只作用于 `Value` 包装，编译产物返回的是裸 `double`。

### 1.2 成因

两条路径对除法的实现互不相同：

| 路径 | 实现 | 除数为 0 时 |
|------|------|-------------|
| 解释器 | `../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/runtime/ExpressionInterpreter.java#BINARY_EVALUATORS` 里 DIV 的 lambda 显式判断 `divisor == 0`，注释写明 Molang allows division by zero, which is always equal to 0 | 0 |
| 编译器 | `../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/runtime/MolangCompilingVisitor.java#visitBinary` 的 `case DIV` 只发 `DDIV` | `0.0/0.0` = `NaN`，`x/0` = `INFINITY` |

两条路径何时各走各的：编译一个表达式时，`../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/runtime/ExpressionInliner.java#visitBinary` 先用 `../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/runtime/IsConstantExpression.java#visitBinary` 判断子树是否常量，是则用解释器求值、把该子树折成字面量，否则留给编译器生成字节码。因此纯字面量的除法走解释器、含 `query.*` / `v.*` / 函数调用的除法走编译器，**同一个算式的两种写法结果不同**。

### 1.3 决策

**D1-1：让编译路径的 `/` 调用与解释路径同一个实现，除数为 0 时返回 0。**

- 新增 `../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/runtime/MolangArithmetic.java`：`public final class`，私有构造，唯一入口 `public static double divide(double dividend, double divisor)`，实现就是解释器那三行（除数为 0 返回 0，否则返回商）。
  - 必须是 **public**：生成的脚本类由 `MolangCompiler` 内部的 `MolangClassLoader` 定义，与 `MolangArithmetic` 不同 classloader，运行期包不同，包级私有会 `IllegalAccessError`。
- `MolangCompilingVisitor` 的 `case DIV` 改为「左右各求值一次，再 `INVOKESTATIC` 调 `MolangArithmetic.divide`」。
- `ExpressionInterpreter` 的 DIV lambda 改为调用同一方法，注释指向该类。这样两条路径**在实现层面不可能再漂移**。

被否决的做法：

| 做法 | 否决理由 |
|------|----------|
| 在字节码里内联「判 0 分支」 | 判断逻辑会成为第二份实现，与解释器各写各的，本次的漂移就是这样产生的 |
| 只改内容包，把除法写成三目保护 | 引擎缺陷固化到下游；下游内容包里同一写法有 26 处，另两个内容包还会再遇到；上游自己就有 `MochaMath#mod` 一类同族风险要处理 |
| 只把 `0/0` 归零、保留 `x/0` 的 `INFINITY` | 与解释器不一致（它对任何 `divisor == 0` 都返回 0），且 `INFINITY` 在骨骼矩阵里同样是坏值 |
| 在动画求值链上兜一层 `isFinite` | 掩盖症状；`NaN` 只是除零的表现，别处（如未绑定查询参与除法）还会再造 |

## 2. D2：多级左值赋值编译成空方法体

### 2.1 现象

`MolangCompilingVisitor#visitBinary` 开头的 ASSIGN 分支只处理「左值是 `IdentifierExpression` 的单一属性」两种情况（`temp`/`t` 与 `variable`/`v`）。左值是**多级访问**（`v.roaming.leg_armor = 1;`）时既不进分支，也不在后面的 `switch (op)` 里有对应 `case`，函数返回 `null`；`../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/runtime/MolangCompiler.java#compileBytecode` 对 `null` 的处理是「补一个同类型转换 + `DRETURN`」，于是生成的 `evaluate` 方法体只有一个 `dreturn`：

```
Bytecode:
  0000000: af          # dreturn，栈是空的
```

调用即 `java.lang.VerifyError: Operand stack underflow`。

### 2.2 影响面

下游 ARMS-Core 内容包实测（`spark_modules/arms_core.builtin` 下 4 个动画文件，2323 条 molang 字面量）：3 条命中本缺陷。

| 位置 | 表达式 |
|------|--------|
| `bmpt_main.animation.json` 的 `pre_parallel0` timeline | `v.roaming.leg_armor=1;` |
| `bmpt_main.animation.json` 的 `pre_parallel0` timeline | `v.roaming.player_size=1;` |
| `bmpt_tac.animation.json` 的 `tac:*` timeline（多处，同一字符串） | `v.roaming.trappings=1;` |

`pre_parallel0` 是下游首版启用的常驻并行层之一（`../ARMS-Core/src/main/java/io/github/sweetzonzi/arms_core/common/control/presentation/MechaAnimPreset.java#PERSISTENT_SLOT_ANIMATIONS`），它的 timeline 在动画首帧命中：`../Spark-Core/src/main/java/cn/solarmoon/spark_core/animation/anim/AnimNotify.kt#check` 对 `time == 0f` 的点在 `lastTime < 0f` 且 `t == 0f` 时触发处理器，而 `../Spark-Core/src/main/java/cn/solarmoon/spark_core/animation/anim/AnimInstance.kt#init` 把 `origin.timeline` 的每一条都注册成了这样的点，处理器体里就是 `script.evalAsDouble(this)`。

**实机待确认**：这条 timeline 是否真的被走到（进 `pre_parallel0` 之后第一次 `AnimInstance.tick()`）、以及下游日志里是否已有 `VerifyError`。若还没有，说明该注意点尚未被触发，但字节码本身已经非法，属确定性缺陷。

### 2.3 决策

**D2-1：补全多级左值赋值，走与解释器同一条语义（`ObjectValue#set`）。**

解释器的赋值分支（`ExpressionInterpreter` 的 BINARY_EVALUATORS 末项）做法是：把左值的「对象」求值成 `Value`，若是 `MutableObjectBinding` 就 `set(property, val)`。编译器要为多级左值生成等价序列：求右值 → 转 `NumberValue` → 求「左值的对象」这一级（例如 `v.roaming` 就是 `getVariableStorage().get("roaming")`）→ 若为 `ObjectValue` 则调 `ObjectValue#set(String, Value)` → 把右值留下作为表达式结果。

**D2-2：`visitBinary` 返回 `null` 必须响亮失败，不允许产出空方法体。**

`null` 的语义是「这个算子或这个形状没实现」，而现状是调用方把它当成「表达式无返回值」继续生成 `DRETURN`，产出**不可加载**的类。修法：让 `visitBinary` 的兜底分支抛 `IllegalArgumentException`，异常信息里带上表达式原文与算子名，便于内容作者定位（把「静默产出坏字节码」变成「编译期明确报错」）。

## 3. D3：布尔上下文里的未绑定查询（不修引擎）

测到两类 `VerifyError: Bad type on operand stack`：

| 位置 | 表达式 |
|------|--------|
| `bmpt_tac` 的 `tac:idle.RightLeg.rotation` | `(!query.is_sneaking)?((ysm.head_yaw<0)?…):…` |
| `bmpt_main` 的 `parallel1_2` timeline | `v.h2_pitch_min=q.is_sneaking&&v.ground_speed==0?…:…` |

成因是「`!` 与 `&&` 会把子表达式按 `BOOLEAN` 期望类型编译（`../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/runtime/MolangCompilingVisitor.java#visitUnary` 的 `LOGICAL_NEGATION` 与同文件 `#visitBinary` 的 `AND`/`OR`），而**未绑定**的 `query.*` 走的是 `../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/runtime/MolangCompilingVisitor.java#visitAccess` 末尾那条兜底分支——它无视期望类型直接发 `DCONST_0`（`double`），随后 `IFEQ` / `IFNE` 期待 `int`」。绑定后的 `@QueryBinding` 方法返回 `double`，会经 `../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/util/AsmUtil.java#addCast` 的 `double → boolean` 转换，类型是对的。

**决策：不在引擎侧改这条兜底分支**，由下游把内容包真正用到的查询绑定齐（`../ARMS-Core/docs/MoLang查询面接入实现计划.md` 的 C 批次）。理由：兜底分支是「这个命名空间没人提供」的降级路径，给它补类型转换只会把「内容依赖了不存在的查询」这件事继续藏起来；`DCONST_0` 的裸 `double` 在非布尔上下文里是合法且正确的降级值。本条的**复核判据**写在下游那条全量用例里：同一批表达式在绑定上下文上不得再抛异常。

## 4. D4：一元负号后紧跟成员函数调用被解析错位

### 4.1 现象

以一元负号开头、被调函数是成员访问的表达式（`-math.clamp(x, 0, 40)`）静默取 0。在下游 ARMS-Core 的 JUnit 车道上逐条实测（编译路径的上下文提供 `q.ground_speed = 10`）：

| 表达式 | 实测 | 期望 |
|--------|------|------|
| `-math.abs(3)` | 0（编译与解释两条路径一致） | -3 |
| `-math.clamp(q.ground_speed*4,0,40)`（编译路径） | 0 | -40 |
| `0-math.clamp(q.ground_speed*4,0,40)`（编译路径） | -40 | -40，正确 |
| `-7.5-math.clamp(q.ground_speed*3,0,30)`（编译路径） | -37.5 | -37.5，正确 |
| `!q.is_sneaking`（编译路径） | 1 | 1，正确（`!` 有 §4.2 所述的既有修复） |

第三、四行是正面对照：负号后面先接字面量（再走二元减）时解析正确，只有「负号直接贴住函数调用」这一形状错位。第二行与第四行的差别不能与第一行混为一谈——查询式表达式的解释路径结果取决于求值对象，此处不列。

### 4.2 成因

`../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/parser/MolangParserImpl.java#parseSingle` 的 `case SUB` 用 `parseSingle` 取负号的操作数：对 `math.clamp` 拿到的是成员访问 `Access(math, clamp)`，而 `(` 仍留在词法流里；回到外层 `#parseCompoundExpression` 的循环后，`#parseCompound` 见到 `LPAREN` 就把**整个取负表达式**当成被调函数，构造出：

```
Call(Unary(ARITHMETICAL_NEGATION)(Access(math, clamp)), [x, 0, 40])
```

两条路径于是都取不到这个函数：编译路径走 `../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/runtime/MolangCompilingVisitor.java#visitCall` 的兜底分支（先打一条 `Unbound function call` 警告，再发 `DCONST_0`），解释路径走 `../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/runtime/ExpressionInterpreter.java#visitCall` 的 `Value.nil()` 分支。`!` 的同一形状已经修过（`#parseSingle` 的 `case BANG` 里有一段 `parseCompound` 补解析），`-` 这一支没有。

### 4.3 影响面

下游 ARMS-Core 的内容包（`spark_modules/arms_core.builtin` 下 4 个动画文件）里 8 处：`bmpt_main.animation.json:2861`、`:2932`、`:2935` 与 `bmpt_tac.animation.json:1503`、`:1574`、`:1577` 是 `-math.clamp(query.ground_speed*4,0,40)` 与 `-30-math.clamp(...)` 一类（jump / fly 的手臂旋转，随水平速度摆臂），6 处命中本缺陷；`bmpt_main.animation.json:2789` 与 `bmpt_tac.animation.json:1431` 的 `-7.5-math.clamp(...)` 是正确形状。这些表达式含查询、不会被常量折叠，因此全部走编译路径。

同批排查还发现一处同族缺陷：`MolangCompilingVisitor#visitUnary` 的取负分支在发完 `DNEG` 之后 `return null`。「取负正好是整个表达式」时调用方会补一个 `DRETURN`，所以它碰巧成立；一旦取负是别的节点的操作数（`--x`、`-(-x)`），父节点读返回类型时即 NPE。

### 4.4 决策

**D4-1：`case SUB` 与 `case BANG` 同形处理——负号的操作数后面若紧跟 `(`，把该调用并进取负表达式内部。**

**D4-2：`#visitUnary` 的取负分支返回「被取负值」的 `CompileVisitResult`，不再返回 `null`；操作数没有产出值时抛异常、异常信息带表达式原文。**

D4-2 与 D2-2 是同一条纪律：`null` 只能表示「这个形状没实现」，不能被调用方当成「表达式没有返回值」继续用。被否决的做法：

| 做法 | 否决理由 |
|------|---------|
| 在 `visitCall` 里补认 `Call(Unary(-)(Access), args)` 这种函数表达式 | 语法树上「对函数取负再调用」本就没有语义，补认等于把解析器的错误固化进求值器，且解释路径要再补一份 |
| 不改解析器，改为要求内容包写成 `0-math.clamp(...)` | 引擎缺陷固化到下游内容，且 `-f(x)` 是任何 MoLang 作者都会写的形状 |

## 5. 改动清单

| 文件 | 改动 |
|------|------|
| `src/main/java/cn/solarmoon/spark_core/molang/runtime/MolangArithmetic.java` | 新增。`public static double divide(double, double)`，除数为 0 返回 0 |
| `src/main/java/cn/solarmoon/spark_core/molang/runtime/MolangCompilingVisitor.java` | `case DIV` 改为 `INVOKESTATIC MolangArithmetic.divide`；ASSIGN 分支补多级左值；未实现的算子抛异常（§2.3） |
| `src/main/java/cn/solarmoon/spark_core/molang/runtime/ExpressionInterpreter.java` | DIV lambda 改为调用 `MolangArithmetic.divide`；注释指向该类 |
| `src/main/java/cn/solarmoon/spark_core/molang/parser/MolangParserImpl.java` | `case SUB` 补「负号的操作数后紧跟 `(` 时把该调用并进取负内部」，与 `case BANG` 同形（§4.4 的 D4-1） |
| `src/main/java/cn/solarmoon/spark_core/molang/runtime/MolangCompilingVisitor.java` | `visitUnary` 的取负分支返回被取负值的类型，不再返回 `null`（§4.4 的 D4-2） |
| `src/test/java/cn/solarmoon/spark_core/molang/MolangArithmeticTest.java` | 新增用例（§6 的 A1 组） |
| `src/test/java/cn/solarmoon/spark_core/molang/MolangCompiledPathTest.java` | 新增用例（§6 的 A2/A3 组） |
| `src/test/java/cn/solarmoon/spark_core/molang/MolangParserPrecedenceTest.java` | 新增用例（§6 的 A4 组） |

生成类的加载细节（写代码时别踩）：`MolangCompiler` 用自定义 `MolangClassLoader` 定义生成的类，被调用的静态方法必须是 `public`；`../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/MolangContextRegistry.java#getOrCreateEngine` 按**上下文类**缓存引擎，并用**无参构造**建原型实例来扫 `@QueryBinding`。

## 6. 验证

单元车道：`./gradlew --offline test`（`../Spark-Core` 自己就有 `src/test`，用例走 `org.junit.jupiter.api`）。

| 组 | 用例 | 期望 |
|----|------|------|
| A1 | `MolangArithmetic.divide(1, 0)`、`(0, 0)`、`(-1, 0)`、`(1, -0.0)` | 全部 0 |
| A1 | `divide(5, 2)`、`divide(-6, 3)` | 2.5、-2（正常除法不受影响） |
| A2 | 编译求值 `x/math.abs(x)`（`x` 为 `@QueryBinding` 返回 0）与同一表达式的解释求值 | 两者都为 0，且都不是 `NaN` |
| A2 | 编译求值 `x/0`、`1/0` | 都为 0 |
| A2 | 内容包原式 `-30+(x/math.abs(x)*(1-math.exp(-0.1*math.abs(x)))*30)`，`x = 0` 与 `x = 3` | 0 与 -22.2245…，且都有限 |
| A3 | 编译求值 `v.a.b=1;`（多级左值） | 不抛异常，且 `v.a.b` 读回 1 |
| A3 | 编译求值 `v.STB_pitch=1;`（单级，现状可用） | 仍返回 1（回归） |
| A3 | 对未实现算子构造一个表达式 | 抛带表达式原文的异常，而不是产出坏类 |
| A4 | 编译与解释求值 `-math.abs(3)`、`-math.clamp(10,0,7)`、`-math.lerp(0,6,0.5)` | 两条路径都是 -3 / -7 / -3（缺陷下都是 0） |
| A4 | 编译与解释求值 `-math.abs(3)+1`、`-math.abs(3)*2`、`--math.abs(3)` | -2 / -6 / 3（负号只吃紧邻的那个调用） |
| A4 | 编译与解释求值 `-2*math.abs(3)`、`-7.5-math.clamp(10,0,7)` | -6 / -14.5（负字面量与二元减两个既有形状回归） |

下游回归（改完必须跑）：

1. `../ARMS-Core`：`./gradlew --offline test`；其内容包全量 molang 用例（`../ARMS-Core/docs/MoLang查询面接入实现计划.md` §4 阶段 3）应从「26 条 `NaN` + 33 条异常」变为「0 条 `NaN`、0 条异常」。
2. `../Machine-Max`：`./gradlew --offline test`（它直连 `@QueryBinding` 与 `AnimInstance`）。
3. 三个内容包仓库里所有 `/` 站点复查一遍：均为正常除法或「除数为变量、默认 0」两类，前者不受影响、后者从 `NaN` 变成 0（更安全）。
4. 三个内容包仓库里所有 `-函数调用(...)` 形状复查一遍：ARMS-Core 的 8 处见 §4.3，其余两个内容包未发现（`-7.5-math.clamp(...)` 一类是正确形状）。

下游回归的实测：

| 仓库 | 命令 | 结果 |
|------|------|------|
| `../Spark-Core` | `./gradlew --offline test` | 全部通过（本文件的三组新用例在内） |
| `../ARMS-Core` | `./gradlew --offline test` | 内容包 2323 条表达式全部有限、全部不抛（`MechaPackMolangTest`） |

## 7. 风险与兼容

| 项 | 评估 |
|----|------|
| 语义变化 | `x/0` 从 `INFINITY` / `NaN` 变成 0。Molang 规范与本文档所用的解释器都是这个口径；内容侧不可能依赖 `NaN` / `INFINITY`（它们进矩阵只会让部件消失） |
| 性能 | 每次除法多一次 `INVOKESTATIC` 与一次比较；除法本身已在热路径上，量级可忽略。若后续要省，可在 `MolangCompilingVisitor` 里对「右值是常量且非 0」的情形省掉调用——**先不做**，保持只有一份实现 |
| `math.mod` | `MochaMath#mod` 是 `a % b`，`b == 0` 时得 `NaN`。它是一条 Java 方法，两条路径都调它，**不存在本文这类漂移**；是否给 `math.mod` 也定「除零 = 0」属另一件事，本文不动，记录在此以免下次重复排查 |
| 解析形状变化 | D4-1 让 `-f(x)` 从「静默 0」变成正确值，因此任何依赖那个 0 的内容都会看到数值变化（没有合理依赖：0 并非该表达式的语义）。负号的结合范围只扩到紧邻的那个调用，`-a*b`、`-a-b` 的语法树不变 |
| 复合构建 | `../Spark-Core` 与 `../Machine-Max` / `../BallisticsFramework` / `../ARMS-Core` 共用复合构建，改动后四处都要编译 |

## 8. 落地顺序

1. A1（D1）：`MolangArithmetic` + 两条路径改调用 + A1/A2 用例 → 提交。
2. A2（D2）：多级左值赋值 + 未实现算子抛异常 + A3 用例 → 提交（可与第 1 步同一个提交，见 §0 标注）。
3. 下游：`../ARMS-Core` 的查询面接入计划（`../ARMS-Core/docs/MoLang查询面接入实现计划.md`）随后落地，并用它的全量用例反过来验收本文两条修正。
4. A4（D4）：解析器的 `case SUB` + `visitUnary` 的返回值 + A4 用例 → 提交。这一步在第 3 步的验收过程中追加：全量用例跑绿之后，把内容包里 `-math.clamp(...)` 的日期与之一并核对，才发现这一形状的 6 处一直是 0。

落地状态：第 1~4 步都已实施，验证证据见 §6。
