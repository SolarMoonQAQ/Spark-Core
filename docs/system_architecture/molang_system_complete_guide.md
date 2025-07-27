# Molang 系统完整指南

## 概述

Molang（Motion Language）是 SparkCore 中用于动画和表达式计算的脚本语言系统。它提供了一个强大的表达式引擎，支持数学运算、条件判断、变量存储和函数调用，主要用于动画控制、状态机和动态计算。

## 系统架构

### 核心组件

#### 1. MolangParser（解析器）
- **位置**: `cn.solarmoon.spark_core.molang.core.MolangParser`
- **功能**: 负责解析 Molang 表达式字符串，将其转换为可执行的表达式对象
- **主要方法**:
  - `parseExpression(String)`: 安全解析表达式，出错时返回 DoubleValue.ZERO
  - `parseExpressionUnsafe(String)`: 不安全解析，可能抛出异常
  - `reset()`: 重置解析器状态

#### 2. MolangEngine（引擎）
- **位置**: `cn.solarmoon.spark_core.molang.engine.MolangEngine`
- **功能**: Molang 引擎的入口接口，提供解析和评估功能
- **实现类**: `MolangEngineImpl`
- **主要方法**:
  - `parse(String)`: 解析字符串为表达式列表
  - `parse(Reader)`: 从 Reader 解析表达式

#### 3. ExpressionEvaluator（表达式评估器）
- **位置**: `cn.solarmoon.spark_core.molang.engine.runtime.ExpressionEvaluator`
- **功能**: 执行和评估已解析的表达式
- **实现类**: `ExpressionEvaluatorImpl`
- **特性**: 支持访问者模式，可处理各种类型的表达式

### 词法分析和语法分析

#### MolangLexer（词法分析器）
- **位置**: `cn.solarmoon.spark_core.molang.engine.lexer.MolangLexer`
- **功能**: 将字符流转换为 Token 流
- **支持的 Token 类型**:
  - 数字字面量（FLOAT）
  - 字符串字面量（STRING）
  - 布尔值（TRUE/FALSE）
  - 运算符（+, -, *, /, ==, !=, <, >, <=, >=, &&, ||）
  - 标识符（IDENTIFIER）
  - 括号和分隔符

#### MolangParserImpl（语法分析器）
- **位置**: `cn.solarmoon.spark_core.molang.engine.parser.MolangParserImpl`
- **功能**: 将 Token 流转换为抽象语法树（AST）
- **支持的表达式类型**:
  - 字面量表达式（数字、字符串、布尔值）
  - 二元运算表达式
  - 函数调用表达式
  - 变量访问表达式
  - 结构体访问表达式

## 变量系统

### 变量类型

#### 1. 临时变量（Temp Variables）
- **前缀**: `t.` 或 `temp.`
- **存储**: `TempVariableBinding` + `ITempVariableStorage`
- **生命周期**: 动画结束后自动销毁
- **用途**: 存储临时计算结果

#### 2. 作用域变量（Scoped Variables）
- **前缀**: `v.` 或 `variable.`
- **存储**: `ScopedVariableBinding` + `IScopedVariableStorage`
- **生命周期**: 在动画过程中持续存在
- **用途**: 存储动画状态和中间值

#### 3. 外置变量（Foreign Variables）
- **前缀**: `c.` 或 `context.`
- **存储**: `ForeignVariableBinding` + `IForeignVariableStorage`
- **生命周期**: 由外部系统管理
- **用途**: 存储外部传入的变量和上下文信息

### 变量存储实现

#### VariableStorage
- **位置**: `cn.solarmoon.spark_core.molang.core.storage.VariableStorage`
- **功能**: 统一实现三种变量存储接口
- **特性**:
  - 使用 StringPool 优化字符串存储
  - 支持动态扩容的栈帧
  - 线程安全的公共变量映射

## 内置函数系统

### 数学函数（MathBinding）
- **前缀**: `math.`
- **常量**:
  - `math.pi`: 圆周率
  - `math.e`: 自然常数
- **取整函数**:
  - `math.floor(x)`: 向下取整
  - `math.ceil(x)`: 向上取整
  - `math.round(x)`: 四舍五入
  - `math.trunc(x)`: 截断取整
- **比较函数**:
  - `math.clamp(value, min, max)`: 限制值在范围内
  - `math.max(a, b)`: 取最大值
  - `math.min(a, b)`: 取最小值
- **经典数学函数**:
  - `math.abs(x)`: 绝对值
  - `math.exp(x)`: 指数函数
  - `math.ln(x)`: 自然对数
  - `math.sqrt(x)`: 平方根
  - `math.mod(a, b)`: 取模
  - `math.pow(base, exp)`: 幂运算
- **三角函数**:
  - `math.sin(x)`: 正弦（角度制）
  - `math.cos(x)`: 余弦（角度制）
  - `math.asin(x)`: 反正弦
  - `math.acos(x)`: 反余弦
  - `math.atan(x)`: 反正切
  - `math.atan2(y, x)`: 双参数反正切
- **实用工具**:
  - `math.lerp(a, b, t)`: 线性插值
  - `math.lerprotate(a, b, t)`: 旋转插值
  - `math.randomSeed(seed)`: 设置随机种子
  - `math.random_integer(min, max)`: 随机整数
  - `math.die_roll(sides, count)`: 骰子投掷
  - `math.hermite_blend(t)`: 埃尔米特混合

### 查询函数（QueryBinding）
- **前缀**: `q.` 或 `query.`
- **时间相关**:
  - `q.anim_time`: 当前动画时间
  - `q.moon_phase`: 月相
  - `q.time_of_day`: 一天中的时间（标准化）
  - `q.time_stamp`: 时间戳
- **实体相关**:
  - `q.head_x_rotation`: 头部X轴旋转
  - `q.head_y_rotation`: 头部Y轴旋转
  - `q.yaw_speed`: 偏航速度
  - `q.ground_speed`: 地面速度
  - `q.vertical_speed`: 垂直速度
  - `q.walk_distance`: 行走距离
  - `q.distance_from_camera`: 距离摄像机距离
- **状态查询**:
  - `q.is_on_ground`: 是否在地面
  - `q.is_in_water`: 是否在水中
  - `q.is_on_fire`: 是否着火
  - `q.has_rider`: 是否有骑手
  - `q.is_riding`: 是否在骑乘
- **生物实体相关**:
  - `q.health`: 生命值
  - `q.max_health`: 最大生命值
  - `q.hurt_time`: 受伤时间
  - `q.is_eating`: 是否在进食
  - `q.is_sleeping`: 是否在睡觉
  - `q.is_using_item`: 是否在使用物品
- **物品相关**:
  - `q.max_durability(slot)`: 最大耐久度
  - `q.remaining_durability(slot)`: 剩余耐久度
  - `q.equipped_item_all_tags(slot, tags...)`: 装备物品包含所有标签
  - `q.equipped_item_any_tag(slot, tags...)`: 装备物品包含任意标签
- **环境相关**:
  - `q.biome_has_all_tags(tags...)`: 生物群系包含所有标签
  - `q.biome_has_any_tag(tags...)`: 生物群系包含任意标签
  - `q.relative_block_has_all_tags(x, y, z, tags...)`: 相对位置方块包含所有标签
  - `q.relative_block_has_any_tag(x, y, z, tags...)`: 相对位置方块包含任意标签

## 值类型系统

### IValue 接口
- **位置**: `cn.solarmoon.spark_core.molang.core.value.IValue`
- **功能**: 所有 Molang 值的基础接口
- **主要方法**:
  - `evalAsDouble(ExpressionEvaluator)`: 评估为双精度浮点数
  - `evalAsBoolean(ExpressionEvaluator)`: 评估为布尔值
  - `evalUnsafe(ExpressionEvaluator)`: 不安全评估，可能抛出异常

### 具体值类型

#### DoubleValue
- **功能**: 存储双精度浮点数常量
- **特性**: 不可变记录类，提供常用常量（ZERO, ONE）

#### MolangValue
- **功能**: 存储已解析的 Molang 表达式
- **特性**: 
  - 保存原始表达式字符串
  - 包含已解析的表达式数组
  - 支持序列化和反序列化

#### Vector3k
- **功能**: 三维向量值，包含三个 IValue 组件
- **用途**: 用于位置、旋转、缩放等三维数据

## 绑定系统

### PrimaryBinding（主绑定）
- **位置**: `cn.solarmoon.spark_core.molang.core.binding.PrimaryBinding`
- **功能**: 管理所有内置绑定和外部绑定
- **包含的绑定**:
  - `math`: 数学函数绑定
  - `query`/`q`: 查询函数绑定
  - `variable`/`v`: 作用域变量绑定
  - `context`/`c`: 外置变量绑定
  - `temp`/`t`: 临时变量绑定
  - `loop`: 循环函数
  - `for_each`: 遍历函数

### 扩展绑定系统

#### MolangBindingRegisterEvent
- **位置**: `cn.solarmoon.spark_core.event.MolangBindingRegisterEvent`
- **功能**: 允许模组注册自定义 Molang 绑定
- **使用方式**: 监听此事件并添加自定义绑定

#### MolangQueryRegisterEvent
- **位置**: `cn.solarmoon.spark_core.event.MolangQueryRegisterEvent`
- **功能**: 允许模组注册自定义查询函数
- **示例**: Spirit of Fight 模组注册 `charging_time` 查询

## 与动画系统的集成

### IAnimatable 接口
- **位置**: `cn.solarmoon.spark_core.animation.IAnimatable`
- **功能**: 定义可动画对象的接口
- **Molang 相关属性**:
  - `tempStorage`: 临时变量存储
  - `scopedStorage`: 作用域变量存储
  - `foreignStorage`: 外置变量存储
  - `animController`: 动画控制器

### AnimController
- **位置**: `cn.solarmoon.spark_core.animation.anim.play.AnimController`
- **功能**: 动画控制器，使用 Molang 进行动画逻辑控制
- **Molang 应用**:
  - 动画条件判断
  - 动画参数计算
  - 状态转换逻辑

## 工具类

### StringPool
- **位置**: `cn.solarmoon.spark_core.molang.core.util.StringPool`
- **功能**: 字符串池化，优化内存使用
- **特性**: 线程安全的字符串到整数映射

### MolangUtils
- **位置**: `cn.solarmoon.spark_core.molang.core.util.MolangUtils`
- **功能**: Molang 相关的实用工具函数
- **主要功能**:
  - 时间标准化
  - 资源位置解析
  - 装备槽位解析

### ValueConversions
- **位置**: `cn.solarmoon.spark_core.molang.engine.runtime.binding.ValueConversions`
- **功能**: 值类型转换工具
- **支持的转换**:
  - 转换为布尔值
  - 转换为浮点数
  - 转换为整数

## 使用示例

### 基本表达式
```molang
// 数学运算
math.sin(q.anim_time * 180) * 0.5

// 条件判断
q.is_on_ground ? 1.0 : 0.0

// 变量使用
v.my_variable = q.health / q.max_health;
return v.my_variable > 0.5 ? 1.0 : 0.0;

// 函数调用
math.lerp(0, 1, math.clamp(q.anim_time, 0, 1))
```

### 动画控制
```molang
// 根据生命值控制动画速度
q.health / q.max_health

// 根据移动状态切换动画
q.ground_speed > 0.1 ? 'walk' : 'idle'

// 复杂的状态判断
q.is_on_ground && !q.is_in_water && q.ground_speed > 0.5
```

## 性能优化

### 字符串池化
- 使用 StringPool 减少字符串对象创建
- 将字符串映射为整数进行快速比较

### 表达式缓存
- 解析后的表达式被缓存在 MolangValue 中
- 避免重复解析相同的表达式

### 变量存储优化
- 使用数组存储临时变量，提供 O(1) 访问
- 使用 FastUtil 集合优化内存使用

## 错误处理

### 解析错误
- `parseExpression()` 方法捕获所有异常，返回 DoubleValue.ZERO
- `parseExpressionUnsafe()` 方法抛出 ParseException

### 运行时错误
- `evalAsDouble()` 和 `evalAsBoolean()` 方法捕获异常，返回默认值
- `evalUnsafe()` 方法可能抛出异常，需要调用者处理

### 日志记录
- 使用 SparkCore.LOGGER 记录解析和评估错误
- 提供详细的错误信息用于调试

## 扩展开发

### 添加自定义函数
1. 实现 `Function` 接口
2. 在 `MolangBindingRegisterEvent` 中注册
3. 指定函数名和绑定前缀

### 添加自定义查询
1. 继承适当的基类（如 `EntityFunction`）
2. 在 `MolangQueryRegisterEvent` 中注册
3. 实现查询逻辑

### 添加自定义变量类型
1. 实现相应的存储接口
2. 创建对应的绑定类
3. 在 PrimaryBinding 中注册

## 最佳实践

### 表达式编写
- 使用有意义的变量名
- 避免过于复杂的嵌套表达式
- 合理使用括号明确运算优先级

### 性能考虑
- 避免在高频调用的地方使用复杂表达式
- 将复杂计算结果存储在变量中
- 使用适当的变量类型（临时 vs 作用域）

### 调试技巧
- 使用 `debug_output()` 函数输出调试信息
- 分步骤验证复杂表达式
- 检查日志中的错误信息

## 总结

SparkCore 的 Molang 系统提供了一个完整的表达式语言实现，支持：
- 完整的词法和语法分析
- 丰富的内置函数库
- 灵活的变量系统
- 与动画系统的深度集成
- 良好的扩展性和性能优化

该系统为动画控制、状态机和动态计算提供了强大的脚本支持，是 SparkCore 动画系统的核心组件。
