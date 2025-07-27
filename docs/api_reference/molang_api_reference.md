# Molang API 参考文档

## 核心 API

### MolangParser

#### 类路径
`cn.solarmoon.spark_core.molang.core.MolangParser`

#### 构造函数
```java
public MolangParser(Map<String, ObjectBinding> extraBindings)
```
- **参数**: `extraBindings` - 额外的绑定映射
- **描述**: 创建一个新的 Molang 解析器实例

#### 主要方法

##### parseExpression
```java
public IValue parseExpression(String molangExpression)
```
- **参数**: `molangExpression` - Molang 表达式字符串
- **返回值**: `IValue` - 解析后的值对象
- **描述**: 安全解析 Molang 表达式，出错时返回 `DoubleValue.ZERO`
- **异常**: 不抛出异常，错误会被记录到日志

##### parseExpressionUnsafe
```java
public IValue parseExpressionUnsafe(String molangExpression) throws ParseException
```
- **参数**: `molangExpression` - Molang 表达式字符串
- **返回值**: `IValue` - 解析后的值对象
- **描述**: 不安全解析 Molang 表达式
- **异常**: `ParseException` - 解析失败时抛出

##### getConstant
```java
public IValue getConstant(double value)
```
- **参数**: `value` - 双精度浮点数值
- **返回值**: `IValue` - 常量值对象
- **描述**: 创建一个双精度浮点数常量

##### reset
```java
public void reset()
```
- **描述**: 重置解析器状态，清理变量存储

### IValue 接口

#### 类路径
`cn.solarmoon.spark_core.molang.core.value.IValue`

#### 主要方法

##### evalAsDouble
```java
default double evalAsDouble(ExpressionEvaluator<?> evaluator)
default double evalAsDouble(IAnimatable<?> animatable)
```
- **参数**: 
  - `evaluator` - 表达式评估器
  - `animatable` - 可动画对象
- **返回值**: `double` - 评估结果的双精度浮点数值
- **描述**: 评估表达式并转换为双精度浮点数

##### evalAsBoolean
```java
default boolean evalAsBoolean(ExpressionEvaluator<?> evaluator)
default boolean evalAsBoolean(IAnimatable<?> animatable)
```
- **参数**: 
  - `evaluator` - 表达式评估器
  - `animatable` - 可动画对象
- **返回值**: `boolean` - 评估结果的布尔值
- **描述**: 评估表达式并转换为布尔值

##### evalUnsafe
```java
Object evalUnsafe(ExpressionEvaluator<?> evaluator) throws Exception
```
- **参数**: `evaluator` - 表达式评估器
- **返回值**: `Object` - 评估结果对象
- **描述**: 不安全评估表达式，可能抛出异常
- **异常**: `Exception` - 评估失败时抛出

### MolangValue

#### 类路径
`cn.solarmoon.spark_core.molang.core.value.MolangValue`

#### 静态方法

##### parse
```java
public static IValue parse(String expression)
```
- **参数**: `expression` - Molang 表达式字符串
- **返回值**: `IValue` - 解析后的值对象
- **描述**: 解析字符串为 Molang 表达式

##### deParse
```java
public static String deParse(IValue value)
```
- **参数**: `value` - IValue 对象
- **返回值**: `String` - 编码后的字符串
- **描述**: 将 IValue 对象编码为字符串

#### 实例方法

##### getOriginalExpressions
```java
public String getOriginalExpressions()
```
- **返回值**: `String` - 原始表达式字符串
- **描述**: 获取原始的 Molang 表达式字符串

### DoubleValue

#### 类路径
`cn.solarmoon.spark_core.molang.core.value.DoubleValue`

#### 构造函数
```java
public DoubleValue(double value)
```
- **参数**: `value` - 双精度浮点数值

#### 常量
```java
public static final DoubleValue ONE = new DoubleValue(1);
public static final DoubleValue ZERO = new DoubleValue(0);
```

#### 静态方法

##### getValue
```java
public static double getValue(IValue value)
```
- **参数**: `value` - IValue 对象
- **返回值**: `double` - 双精度浮点数值
- **描述**: 从 IValue 对象中提取双精度浮点数值

### Vector3k

#### 类路径
`cn.solarmoon.spark_core.molang.core.value.Vector3k`

#### 构造函数
```java
public Vector3k(IValue x, IValue y, IValue z)
```
- **参数**: 
  - `x` - X 轴分量
  - `y` - Y 轴分量
  - `z` - Z 轴分量

#### 方法

##### eval
```java
public Vector3f eval(ExpressionEvaluator<?> evaluator)
```
- **参数**: `evaluator` - 表达式评估器
- **返回值**: `Vector3f` - 三维向量
- **描述**: 评估三个分量并返回 Vector3f 对象

## 变量系统 API

### ITempVariableStorage

#### 类路径
`cn.solarmoon.spark_core.molang.core.storage.ITempVariableStorage`

#### 方法
```java
Object getTemp(int index)
void setTemp(int index, Object value)
```
- **描述**: 临时变量存储接口，用于存储动画结束后自动销毁的变量

### IScopedVariableStorage

#### 类路径
`cn.solarmoon.spark_core.molang.core.storage.IScopedVariableStorage`

#### 方法
```java
Object getScoped(int name)
void setScoped(int name, Object value)
```
- **描述**: 作用域变量存储接口，用于存储动画过程中的变量

### IForeignVariableStorage

#### 类路径
`cn.solarmoon.spark_core.molang.core.storage.IForeignVariableStorage`

#### 方法
```java
Object getPublic(int name)
Object getPublic(String name)
```
- **描述**: 外置变量存储接口，用于存储外部传入的变量

### VariableStorage

#### 类路径
`cn.solarmoon.spark_core.molang.core.storage.VariableStorage`

#### 构造函数
```java
public VariableStorage()
```

#### 方法

##### 临时变量操作
```java
public Object getTemp(int index)
public void setTemp(int index, Object value)
```

##### 作用域变量操作
```java
public Object getScoped(int name)
public void setScoped(int name, Object value)
```

##### 外置变量操作
```java
public Object getPublic(int name)
public Object getPublic(String name)
public void setPublic(String name, Object value)
public void setPublic(int name, Object value)
```

##### 初始化
```java
public void initialize(PooledStringHashSet publicVariableNames)
```
- **参数**: `publicVariableNames` - 公共变量名集合
- **描述**: 初始化变量存储，清理临时和作用域变量

## 绑定系统 API

### ObjectBinding

#### 类路径
`cn.solarmoon.spark_core.molang.engine.runtime.binding.ObjectBinding`

#### 方法
```java
Object getProperty(String name)
```
- **参数**: `name` - 属性名
- **返回值**: `Object` - 属性值
- **描述**: 获取指定名称的属性

#### 常量
```java
ObjectBinding EMPTY = name -> null;
```

### PrimaryBinding

#### 类路径
`cn.solarmoon.spark_core.molang.core.binding.PrimaryBinding`

#### 构造函数
```java
public PrimaryBinding(Map<String, ObjectBinding> extraBindings)
```
- **参数**: `extraBindings` - 额外的绑定映射

#### 方法

##### popStackFrame
```java
public void popStackFrame()
```
- **描述**: 弹出栈帧，清理临时变量

##### reset
```java
public void reset()
```
- **描述**: 重置绑定状态

## 函数系统 API

### Function 接口

#### 类路径
`cn.solarmoon.spark_core.molang.engine.runtime.Function`

#### 方法
```java
Object evaluate(ExecutionContext<?> context, ArgumentCollection arguments)
boolean validateArgumentSize(int size)
```
- **描述**: 函数接口，用于实现自定义 Molang 函数

### ArgumentCollection

#### 类路径
`cn.solarmoon.spark_core.molang.engine.runtime.Function.ArgumentCollection`

#### 方法
```java
double getAsDouble(ExecutionContext<?> context, int index)
int getAsInt(ExecutionContext<?> context, int index)
String getAsString(ExecutionContext<?> context, int index)
boolean getAsBoolean(ExecutionContext<?> context, int index)
int size()
```

### 内置函数基类

#### EntityFunction
```java
// 位置：cn.solarmoon.spark_core.molang.core.builtin.query.EntityFunction
public abstract class EntityFunction implements Function {
    protected abstract Object eval(
        ExecutionContext<IAnimatable<Entity>> context, 
        ArgumentCollection arguments
    );
}
```

#### LivingEntityFunction
```java
// 位置：cn.solarmoon.spark_core.molang.core.builtin.query.LivingEntityFunction
public abstract class LivingEntityFunction implements Function {
    protected abstract Object eval(
        ExecutionContext<IAnimatable<LivingEntity>> context, 
        ArgumentCollection arguments
    );
}
```

## 事件系统 API

### MolangBindingRegisterEvent

#### 类路径
`cn.solarmoon.spark_core.event.MolangBindingRegisterEvent`

#### 构造函数
```java
public MolangBindingRegisterEvent()
public MolangBindingRegisterEvent(Map<String, ObjectBinding> extraBindings)
```

#### 方法
```java
public Map<String, ObjectBinding> getBindings()
```
- **返回值**: `Map<String, ObjectBinding>` - 绑定映射
- **描述**: 获取注册的绑定映射

### MolangQueryRegisterEvent

#### 类路径
`cn.solarmoon.spark_core.event.MolangQueryRegisterEvent`

#### 构造函数
```java
public MolangQueryRegisterEvent(QueryBinding binding)
```

#### 属性
```java
public final QueryBinding binding
```
- **描述**: 查询绑定对象，用于注册自定义查询函数

## 工具类 API

### StringPool

#### 类路径
`cn.solarmoon.spark_core.molang.core.util.StringPool`

#### 静态方法
```java
public static int computeIfAbsent(String str)
public static int getName(String str)
public static String getString(int name)
```

#### 常量
```java
public static final int NONE = Integer.MIN_VALUE;
public static final String EMPTY = "";
```

### MolangUtils

#### 类路径
`cn.solarmoon.spark_core.molang.core.util.MolangUtils`

#### 静态方法

##### normalizeTime
```java
public static float normalizeTime(long timestamp)
```
- **参数**: `timestamp` - 时间戳
- **返回值**: `float` - 标准化时间（0-1）
- **描述**: 将时间戳标准化为 0-1 范围的浮点数

##### parseResourceLocation
```java
public static ResourceLocation parseResourceLocation(IAnimatable<?> context, String value)
```
- **参数**: 
  - `context` - 动画上下文
  - `value` - 资源位置字符串
- **返回值**: `ResourceLocation` - 资源位置对象
- **描述**: 解析资源位置字符串

##### parseSlotType
```java
public static EquipmentSlot parseSlotType(IAnimatable<?> context, String value)
```
- **参数**: 
  - `context` - 动画上下文
  - `value` - 装备槽位字符串
- **返回值**: `EquipmentSlot` - 装备槽位枚举
- **描述**: 解析装备槽位字符串

### ValueConversions

#### 类路径
`cn.solarmoon.spark_core.molang.engine.runtime.binding.ValueConversions`

#### 静态方法
```java
public static boolean asBoolean(Object obj)
public static float asFloat(Object obj)
public static int asInt(Object obj)
public static double asDouble(Object obj)
```
- **参数**: `obj` - 要转换的对象
- **返回值**: 对应类型的值
- **描述**: 类型转换工具方法

## 使用示例

### 基本解析和评估
```java
// 创建解析器
MolangParser parser = new MolangParser(null);

// 解析表达式
IValue value = parser.parseExpression("math.sin(q.anim_time * 180) * 0.5");

// 评估表达式
double result = value.evalAsDouble(animatable);
```

### 自定义函数注册
```java
@SubscribeEvent
public static void onMolangBindingRegister(MolangBindingRegisterEvent event) {
    Map<String, ObjectBinding> bindings = event.getBindings();
    
    // 创建自定义绑定
    ContextBinding customBinding = new ContextBinding();
    customBinding.function("my_function", new MyCustomFunction());
    
    bindings.put("custom", customBinding);
}
```

### 自定义查询注册
```java
@SubscribeEvent
public static void onMolangQueryRegister(MolangQueryRegisterEvent event) {
    event.binding.entityVar("my_property", entity -> {
        // 返回实体的自定义属性
        return entity.getAnimatable().getCustomProperty();
    });
}
```

### 变量操作
```java
// 获取变量存储
VariableStorage storage = (VariableStorage) animatable.getScopedStorage();

// 设置变量
storage.setScoped(StringPool.computeIfAbsent("my_var"), 42.0);

// 获取变量
Object value = storage.getScoped(StringPool.computeIfAbsent("my_var"));
```

## 错误处理

### 常见异常类型
- `ParseException`: 解析错误
- `IllegalArgumentException`: 参数错误
- `NullPointerException`: 空指针异常

### 错误处理最佳实践
1. 使用 `parseExpression()` 而不是 `parseExpressionUnsafe()` 进行安全解析
2. 使用 `evalAsDouble()` 和 `evalAsBoolean()` 进行安全评估
3. 检查日志中的错误信息进行调试
4. 验证函数参数数量和类型

## 性能建议

1. **重用解析器实例**: 避免频繁创建 MolangParser 实例
2. **缓存解析结果**: 将常用表达式的解析结果缓存起来
3. **使用 StringPool**: 利用字符串池化减少内存使用
4. **避免复杂表达式**: 在高频调用的地方避免使用过于复杂的表达式
5. **合理使用变量类型**: 根据生命周期选择合适的变量类型
