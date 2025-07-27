# Molang 引擎技术实现详解

## 引擎架构概览

Molang 引擎采用经典的编译器架构，包含词法分析、语法分析、AST 构建和表达式评估四个主要阶段。

```
源代码字符串 → 词法分析 → Token流 → 语法分析 → AST → 表达式评估 → 结果值
```

## 词法分析器（Lexer）实现

### MolangLexerImpl 核心机制

```java
// 位置：cn.solarmoon.spark_core.molang.engine.lexer.MolangLexerImpl
final class MolangLexerImpl implements MolangLexer {
    private final Reader reader;
    private final Cursor cursor = new Cursor();
    private int next;
    private Token lastToken = null;
    private Token token = null;
}
```

### Token 类型定义

| Token 类型 | 描述 | 示例 |
|-----------|------|------|
| FLOAT | 浮点数字面量 | `3.14`, `42`, `0.5` |
| STRING | 字符串字面量 | `'hello'`, `'world'` |
| TRUE/FALSE | 布尔字面量 | `true`, `false` |
| IDENTIFIER | 标识符 | `variable_name`, `function_name` |
| PLUS/MINUS | 算术运算符 | `+`, `-` |
| MULTIPLY/DIVIDE | 算术运算符 | `*`, `/` |
| EQUALS/NOT_EQUALS | 比较运算符 | `==`, `!=` |
| LESS_THAN/GREATER_THAN | 比较运算符 | `<`, `>` |
| AND/OR | 逻辑运算符 | `&&`, `||` |
| LPAREN/RPAREN | 括号 | `(`, `)` |
| DOT | 点操作符 | `.` |
| SEMICOLON | 分号 | `;` |

### 词法分析流程

1. **字符读取**: 从 Reader 逐字符读取
2. **空白符跳过**: 忽略空格、制表符、换行符
3. **Token 识别**: 根据字符模式识别 Token 类型
4. **字符串处理**: 支持单引号字符串，处理转义
5. **数字解析**: 支持整数和浮点数
6. **关键字识别**: 区分标识符和关键字

## 语法分析器（Parser）实现

### MolangParserImpl 核心结构

```java
// 位置：cn.solarmoon.spark_core.molang.engine.parser.MolangParserImpl
final class MolangParserImpl implements MolangParser {
    private final MolangLexer lexer;
    private final ObjectBinding bindings;
    private Object current = UNSET_FLAG;
}
```

### 表达式解析策略

#### 1. 单一表达式解析（parseSingle）
处理不需要左操作数的表达式：
- 字面量（数字、字符串、布尔值）
- 标识符
- 括号表达式
- 函数调用

#### 2. 复合表达式解析（parseCompoundExpression）
使用运算符优先级解析算法：
- 支持左结合和右结合运算符
- 处理运算符优先级
- 构建正确的 AST 结构

#### 3. 运算符优先级表

| 优先级 | 运算符 | 结合性 |
|--------|--------|--------|
| 10 | `||` | 左结合 |
| 20 | `&&` | 左结合 |
| 30 | `==`, `!=` | 左结合 |
| 40 | `<`, `<=`, `>`, `>=` | 左结合 |
| 50 | `+`, `-` | 左结合 |
| 60 | `*`, `/`, `%` | 左结合 |
| 70 | `!`, `-`(一元) | 右结合 |
| 80 | `.` | 左结合 |
| 90 | `()` | 左结合 |

## AST 节点类型

### 表达式接口层次

```java
// 基础表达式接口
public interface Expression {
    <R> R visit(ExpressionVisitor<R> visitor);
}

// 访问者接口
public interface ExpressionVisitor<R> {
    R visitDouble(DoubleExpression expression);
    R visitString(StringExpression expression);
    R visitBinary(BinaryExpression expression);
    R visitFunction(FunctionExpression expression);
    R visitVariable(VariableExpression expression);
    R visitStruct(StructAccessExpression expression);
    // ... 其他访问方法
}
```

### 具体表达式类型

#### 1. DoubleExpression（数字表达式）
```java
public final class DoubleExpression implements Expression {
    public static final DoubleExpression ZERO = new DoubleExpression(0.0D);
    public static final DoubleExpression ONE = new DoubleExpression(1.0D);
    
    private final double value;
    
    public double value() { return value; }
}
```

#### 2. BinaryExpression（二元表达式）
```java
public final class BinaryExpression implements Expression {
    public enum Op {
        AND(10), OR(10), LESS_THAN(40), EQUALS(30), 
        PLUS(50), MINUS(50), MULTIPLY(60), DIVIDE(60);
        
        private final int precedence;
    }
    
    private final Op op;
    private final Expression left;
    private final Expression right;
}
```

#### 3. FunctionExpression（函数调用表达式）
```java
public final class FunctionExpression implements Expression {
    private final Expression target;
    private final List<Expression> arguments;
}
```

#### 4. VariableExpression（变量表达式）
```java
public final class VariableExpression implements Expression {
    private final Variable target;
}
```

#### 5. StructAccessExpression（结构体访问表达式）
```java
public class StructAccessExpression implements Expression {
    private final Expression left;
    private final int path; // 使用 StringPool 优化
}
```

## 表达式评估器（Evaluator）实现

### ExpressionEvaluatorImpl 核心机制

```java
public final class ExpressionEvaluatorImpl<TEntity> implements ExpressionEvaluator<TEntity> {
    private static final Evaluator[] BINARY_EVALUATORS = {
        // 逻辑运算符
        bool((a, b) -> a.eval() && b.eval()),  // AND
        bool((a, b) -> a.eval() || b.eval()),  // OR
        
        // 比较运算符
        compare((a, b) -> a.eval() < b.eval()), // LESS_THAN
        compare((a, b) -> a.eval() <= b.eval()), // LESS_EQUAL
        
        // 算术运算符
        arithmetic((a, b) -> a.eval() + b.eval()), // PLUS
        arithmetic((a, b) -> a.eval() - b.eval()), // MINUS
        arithmetic((a, b) -> a.eval() * b.eval()), // MULTIPLY
        arithmetic((a, b) -> a.eval() / b.eval()), // DIVIDE
    };
    
    private TEntity entity;
    private Object returnValue;
}
```

### 访问者模式实现

#### 1. 数字表达式评估
```java
@Override
public Object visitDouble(DoubleExpression expression) {
    return expression.value();
}
```

#### 2. 二元表达式评估
```java
@Override
public Object visitBinary(BinaryExpression expression) {
    return BINARY_EVALUATORS[expression.op().index()].eval(
        this,
        expression.left(),
        expression.right()
    );
}
```

#### 3. 函数调用评估
```java
@Override
public Object visitFunction(FunctionExpression expression) {
    Object target = expression.target().visit(this);
    if (target instanceof Function) {
        ArgumentCollection args = new ArgumentCollection(expression.arguments(), this);
        return ((Function) target).evaluate(this, args);
    }
    return null;
}
```

#### 4. 变量访问评估
```java
@Override
public Object visitVariable(VariableExpression expression) {
    return expression.target().evaluate(this);
}
```

### 类型转换机制

#### ValueConversions 工具类
```java
public final class ValueConversions {
    public static boolean asBoolean(Object obj) {
        if (obj == null) return false;
        if (obj instanceof Boolean) return (Boolean) obj;
        if (obj instanceof Number) return ((Number) obj).floatValue() != 0;
        return true;
    }
    
    public static float asFloat(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) return ((Number) obj).floatValue();
        if (obj instanceof Boolean) return ((Boolean) obj) ? 1 : 0;
        return 1;
    }
    
    public static int asInt(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) return ((Number) obj).intValue();
        if (obj instanceof Boolean) return ((Boolean) obj) ? 1 : 0;
        return 1;
    }
}
```

## 绑定系统实现

### ObjectBinding 接口
```java
public interface ObjectBinding {
    Object getProperty(String name);
    
    ObjectBinding EMPTY = name -> null;
}
```

### PrimaryBinding 实现
```java
public class PrimaryBinding implements ObjectBinding {
    protected final Object2ReferenceOpenHashMap<String, Object> bindings;
    protected final ScopedVariableBinding scopedBinding;
    protected final ForeignVariableBinding foreignBinding;
    protected final TempVariableBinding tempBinding;
    
    public PrimaryBinding(Map<String, ObjectBinding> extraBindings) {
        // 注册内置绑定
        bindings.put("math", MathBinding.INSTANCE);
        bindings.put("query", QueryBinding.INSTANCE);
        bindings.put("q", QueryBinding.INSTANCE);
        
        // 注册变量绑定
        bindings.put("variable", scopedBinding);
        bindings.put("v", scopedBinding);
        bindings.put("context", foreignBinding);
        bindings.put("c", foreignBinding);
        bindings.put("temp", tempBinding);
        bindings.put("t", tempBinding);
        
        // 注册外部绑定
        if (extraBindings != null) {
            bindings.putAll(extraBindings);
        }
    }
}
```

### 变量绑定实现

#### ScopedVariableBinding
```java
public class ScopedVariableBinding implements ObjectBinding {
    private final Int2ReferenceOpenHashMap<ScopedVariable> variableMap;
    
    @Override
    public Object getProperty(String name) {
        return variableMap.computeIfAbsent(
            StringPool.computeIfAbsent(name), 
            ScopedVariable::new
        );
    }
    
    private record ScopedVariable(int name) implements AssignableVariable {
        @Override
        public Object evaluate(ExecutionContext<?> context) {
            return ((IAnimatable<Object>) context.entity())
                .getScopedStorage().getScoped(name);
        }
        
        @Override
        public void assign(ExecutionContext<?> context, Object value) {
            ((IAnimatable<Object>) context.entity())
                .getScopedStorage().setScoped(name, value);
        }
    }
}
```

## 内置函数实现

### 函数接口定义
```java
public interface Function {
    Object evaluate(ExecutionContext<?> context, ArgumentCollection arguments);
    boolean validateArgumentSize(int size);
}
```

### 数学函数示例

#### Floor 函数
```java
public class Floor implements Function {
    @Override
    public Object evaluate(ExecutionContext<?> context, ArgumentCollection arguments) {
        return Math.floor(arguments.getAsDouble(context, 0));
    }
    
    @Override
    public boolean validateArgumentSize(int size) {
        return size == 1;
    }
}
```

#### Lerp 函数
```java
public class Lerp implements Function {
    @Override
    public Object evaluate(ExecutionContext<?> context, ArgumentCollection arguments) {
        double a = arguments.getAsDouble(context, 0);
        double b = arguments.getAsDouble(context, 1);
        double t = arguments.getAsDouble(context, 2);
        return a + (b - a) * t;
    }
    
    @Override
    public boolean validateArgumentSize(int size) {
        return size == 3;
    }
}
```

### 查询函数基类

#### EntityFunction
```java
public abstract class EntityFunction implements Function {
    @Override
    public final Object evaluate(ExecutionContext<?> context, ArgumentCollection arguments) {
        if (context.entity() instanceof IAnimatable<?> animatable) {
            if (animatable.getAnimatable() instanceof Entity) {
                return eval((ExecutionContext<IAnimatable<Entity>>) context, arguments);
            }
        }
        return null;
    }
    
    protected abstract Object eval(
        ExecutionContext<IAnimatable<Entity>> context, 
        ArgumentCollection arguments
    );
}
```

## 性能优化技术

### 1. 字符串池化（StringPool）
```java
public class StringPool {
    private static final AtomicInteger COUNTER = new AtomicInteger(0);
    private static final ConcurrentHashMap<String, Integer> POOL = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, String> MAP = new ConcurrentHashMap<>();
    
    public static int computeIfAbsent(String str) {
        return POOL.computeIfAbsent(str, k -> {
            int name = COUNTER.incrementAndGet();
            MAP.put(name, k);
            return name;
        });
    }
}
```

### 2. 快速集合（PooledStringHashMap）
```java
public class PooledStringHashMap<V> extends Int2ReferenceOpenHashMap<V> {
    public V get(String key) {
        int intKey = StringPool.getName(key);
        if (intKey == StringPool.NONE) {
            return null;
        } else {
            return super.get(intKey);
        }
    }
    
    public void put(String key, V value) {
        super.put(StringPool.computeIfAbsent(key), value);
    }
}
```

### 3. 表达式缓存
- 解析后的表达式存储在 MolangValue 中
- 避免重复解析相同的表达式字符串
- 使用原始字符串作为缓存键

### 4. 变量存储优化
```java
public class VariableStorage implements ITempVariableStorage, IScopedVariableStorage {
    private Object[] stackFrame = new Object[TEMP_INIT_CAPACITY];
    private final PooledStringHashMap<VariableValueHolder> scopedMap;
    
    private void ensureStackFrameSize(int size) {
        if (stackFrame.length >= size) return;
        if (size < stackFrame.length * 2) {
            size = stackFrame.length * 2;
        }
        stackFrame = Arrays.copyOf(stackFrame, size);
    }
}
```

## 错误处理机制

### 1. 解析错误处理
```java
public IValue parseExpression(String molangExpression) {
    try {
        return parseExpressionUnsafe(molangExpression);
    } catch (Exception e) {
        SparkCore.LOGGER.error("Failed to parse value \"{}\": {}", molangExpression, e.getMessage());
        return DoubleValue.ZERO;
    }
}
```

### 2. 评估错误处理
```java
default double evalAsDouble(ExpressionEvaluator<?> evaluator) {
    try {
        Object result = evalUnsafe(evaluator);
        double value = ValueConversions.asDouble(result);
        if (!Double.isNaN(value)) {
            return value;
        }
    } catch (Exception e) {
        SparkCore.LOGGER.error("Failed to evaluate molang value.", e);
    }
    return 0;
}
```

### 3. 词法错误处理
```java
if (token.kind() == TokenKind.ERROR) {
    throw new ParseException("Found an invalid token (error): " + token.value(), cursor());
}
```

## 扩展机制

### 1. 事件驱动的绑定注册
```java
public void init() {
    var event = new MolangBindingRegisterEvent();
    ModLoader.postEvent(event);
    extraBindings.putAll(event.getBindings());
    primaryBinding = new PrimaryBinding(event.getBindings());
    engine = MolangEngine.fromCustomBinding(primaryBinding);
}
```

### 2. 模组扩展示例
```kotlin
// Spirit of Fight 模组的扩展
object SOFMolangQueryRegister {
    private fun reg(event: MolangQueryRegisterEvent) {
        event.binding.entityVar("charging_time") { it.animatable.chargingTime }
    }
    
    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::reg)
    }
}
```

## 总结

Molang 引擎的技术实现体现了以下设计原则：

1. **模块化设计**: 清晰分离词法分析、语法分析、AST 构建和表达式评估
2. **访问者模式**: 使用访问者模式实现类型安全的 AST 遍历
3. **性能优化**: 通过字符串池化、快速集合和表达式缓存提升性能
4. **扩展性**: 通过事件系统支持模组扩展
5. **错误处理**: 完善的错误处理机制确保系统稳定性
6. **类型安全**: 强类型系统和类型转换机制

这些技术实现使得 Molang 引擎既高效又灵活，能够满足复杂动画系统的需求。
