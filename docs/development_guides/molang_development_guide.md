# Molang 开发指南

## 概述

本指南面向希望扩展或修改 SparkCore Molang 系统的开发者，涵盖了从基础概念到高级扩展的完整开发流程。

## 开发环境设置

### 前置条件
- Java 17 或更高版本
- NeoForge 开发环境
- 对 SparkCore 项目结构的基本了解

### 项目结构
```
src/main/java/cn/solarmoon/spark_core/molang/
├── core/                          # 核心解析和值系统
│   ├── MolangParser.java         # 主解析器
│   ├── binding/                  # 绑定系统
│   ├── builtin/                  # 内置函数
│   ├── storage/                  # 变量存储
│   ├── util/                     # 工具类
│   └── value/                    # 值类型
├── engine/                       # 引擎实现
│   ├── MolangEngine.java        # 引擎接口
│   ├── lexer/                   # 词法分析器
│   ├── parser/                  # 语法分析器
│   └── runtime/                 # 运行时系统
└── event/                       # 事件系统
```

## 基础开发任务

### 1. 添加新的数学函数

#### 步骤 1: 实现函数类
```java
package cn.solarmoon.spark_core.molang.core.builtin.math;

import cn.solarmoon.spark_core.molang.engine.runtime.ExecutionContext;
import cn.solarmoon.spark_core.molang.engine.runtime.Function;

public class Sigmoid implements Function {
    @Override
    public Object evaluate(ExecutionContext<?> context, ArgumentCollection arguments) {
        double x = arguments.getAsDouble(context, 0);
        return 1.0 / (1.0 + Math.exp(-x));
    }
    
    @Override
    public boolean validateArgumentSize(int size) {
        return size == 1;
    }
}
```

#### 步骤 2: 注册函数
在 `MathBinding.java` 中添加：
```java
function("sigmoid", new Sigmoid());
```

#### 步骤 3: 测试
```molang
math.sigmoid(0)     // 返回 0.5
math.sigmoid(1)     // 返回 0.731...
math.sigmoid(-1)    // 返回 0.268...
```

### 2. 添加新的查询函数

#### 步骤 1: 实现查询类
```java
package cn.solarmoon.spark_core.molang.core.builtin.query;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.engine.runtime.ExecutionContext;
import net.minecraft.world.entity.Entity;

public class EntityAge extends EntityFunction {
    @Override
    protected Object eval(ExecutionContext<IAnimatable<Entity>> context, ArgumentCollection arguments) {
        Entity entity = context.entity().getAnimatable();
        return entity.tickCount / 20.0; // 转换为秒
    }
    
    @Override
    public boolean validateArgumentSize(int size) {
        return size == 0;
    }
}
```

#### 步骤 2: 注册查询
在 `QueryBinding.java` 中添加：
```java
function("entity_age", new EntityAge());
```

#### 步骤 3: 测试
```molang
q.entity_age        // 返回实体存在的时间（秒）
```

### 3. 添加自定义变量类型

#### 步骤 1: 定义存储接口
```java
package cn.solarmoon.spark_core.molang.core.storage;

public interface ICustomVariableStorage {
    Object getCustom(int name);
    void setCustom(int name, Object value);
}
```

#### 步骤 2: 实现变量绑定
```java
package cn.solarmoon.spark_core.molang.core.binding.variable;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.core.util.StringPool;
import cn.solarmoon.spark_core.molang.engine.runtime.AssignableVariable;
import cn.solarmoon.spark_core.molang.engine.runtime.ExecutionContext;
import cn.solarmoon.spark_core.molang.engine.runtime.binding.ObjectBinding;

public class CustomVariableBinding implements ObjectBinding {
    private final Int2ReferenceOpenHashMap<CustomVariable> variableMap = new Int2ReferenceOpenHashMap<>();
    
    @Override
    public Object getProperty(String name) {
        return variableMap.computeIfAbsent(StringPool.computeIfAbsent(name), CustomVariable::new);
    }
    
    private record CustomVariable(int name) implements AssignableVariable {
        @Override
        public Object evaluate(ExecutionContext<?> context) {
            return ((ICustomVariableStorage) context.entity()).getCustom(name);
        }
        
        @Override
        public void assign(ExecutionContext<?> context, Object value) {
            ((ICustomVariableStorage) context.entity()).setCustom(name, value);
        }
    }
}
```

#### 步骤 3: 注册绑定
在 `PrimaryBinding.java` 中添加：
```java
bindings.put("custom", customBinding);
bindings.put("x", customBinding);
```

## 高级开发任务

### 1. 扩展表达式类型

#### 步骤 1: 定义新的 AST 节点
```java
package cn.solarmoon.spark_core.molang.engine.parser.ast;

import org.jetbrains.annotations.NotNull;

public final class ConditionalExpression implements Expression {
    private final Expression condition;
    private final Expression trueExpression;
    private final Expression falseExpression;
    
    public ConditionalExpression(Expression condition, Expression trueExpression, Expression falseExpression) {
        this.condition = condition;
        this.trueExpression = trueExpression;
        this.falseExpression = falseExpression;
    }
    
    @Override
    public <R> R visit(@NotNull ExpressionVisitor<R> visitor) {
        return visitor.visitConditional(this);
    }
    
    // getters...
}
```

#### 步骤 2: 扩展访问者接口
在 `ExpressionVisitor.java` 中添加：
```java
R visitConditional(ConditionalExpression expression);
```

#### 步骤 3: 实现评估逻辑
在 `ExpressionEvaluatorImpl.java` 中添加：
```java
@Override
public Object visitConditional(ConditionalExpression expression) {
    Object conditionResult = expression.condition().visit(this);
    boolean condition = ValueConversions.asBoolean(conditionResult);
    
    if (condition) {
        return expression.trueExpression().visit(this);
    } else {
        return expression.falseExpression().visit(this);
    }
}
```

#### 步骤 4: 扩展解析器
在 `MolangParserImpl.java` 中添加三元运算符解析逻辑。

### 2. 实现自定义数据类型

#### 步骤 1: 定义数据类型
```java
package cn.solarmoon.spark_core.molang.core.value;

public class ColorValue implements IValue {
    private final float red;
    private final float green;
    private final float blue;
    private final float alpha;
    
    public ColorValue(float red, float green, float blue, float alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }
    
    @Override
    public Object evalUnsafe(ExpressionEvaluator<?> evaluator) {
        return this;
    }
    
    @Override
    public double evalAsDouble(ExpressionEvaluator<?> evaluator) {
        // 返回亮度值
        return 0.299 * red + 0.587 * green + 0.114 * blue;
    }
    
    // 其他方法...
}
```

#### 步骤 2: 添加构造函数
```java
public class ColorFunction implements Function {
    @Override
    public Object evaluate(ExecutionContext<?> context, ArgumentCollection arguments) {
        float r = (float) arguments.getAsDouble(context, 0);
        float g = (float) arguments.getAsDouble(context, 1);
        float b = (float) arguments.getAsDouble(context, 2);
        float a = arguments.size() > 3 ? (float) arguments.getAsDouble(context, 3) : 1.0f;
        
        return new ColorValue(r, g, b, a);
    }
    
    @Override
    public boolean validateArgumentSize(int size) {
        return size == 3 || size == 4;
    }
}
```

### 3. 实现模组扩展系统

#### 步骤 1: 创建扩展事件监听器
```java
package com.example.mymod.molang;

import cn.solarmoon.spark_core.event.MolangBindingRegisterEvent;
import cn.solarmoon.spark_core.event.MolangQueryRegisterEvent;
import cn.solarmoon.spark_core.molang.core.binding.ContextBinding;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "mymod", bus = EventBusSubscriber.Bus.MOD)
public class MyModMolangExtensions {
    
    @SubscribeEvent
    public static void onMolangBindingRegister(MolangBindingRegisterEvent event) {
        ContextBinding myBinding = new ContextBinding();
        
        // 添加自定义函数
        myBinding.function("my_function", new MyCustomFunction());
        myBinding.constValue("my_constant", 42.0);
        
        event.getBindings().put("mymod", myBinding);
    }
    
    @SubscribeEvent
    public static void onMolangQueryRegister(MolangQueryRegisterEvent event) {
        // 添加实体相关查询
        event.binding.entityVar("my_entity_property", entity -> {
            // 返回实体的自定义属性
            return getMyEntityProperty(entity.getAnimatable());
        });
        
        // 添加生物实体相关查询
        event.binding.livingEntityVar("my_living_property", entity -> {
            // 返回生物实体的自定义属性
            return getMyLivingProperty(entity.getAnimatable());
        });
    }
    
    private static double getMyEntityProperty(Entity entity) {
        // 实现自定义逻辑
        return 0.0;
    }
    
    private static double getMyLivingProperty(LivingEntity entity) {
        // 实现自定义逻辑
        return 0.0;
    }
}
```

#### 步骤 2: 注册事件监听器
在模组主类中确保事件监听器被正确注册：
```java
@Mod("mymod")
public class MyMod {
    public MyMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        // 事件监听器会通过 @EventBusSubscriber 自动注册
    }
}
```

## 调试和测试

### 1. 单元测试

#### 创建测试类
```java
package cn.solarmoon.spark_core.molang.test;

import cn.solarmoon.spark_core.molang.core.MolangParser;
import cn.solarmoon.spark_core.molang.core.value.IValue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MolangParserTest {
    
    @Test
    public void testBasicArithmetic() {
        MolangParser parser = new MolangParser(null);
        
        IValue result = parser.parseExpression("1 + 2 * 3");
        assertEquals(7.0, result.evalAsDouble(null), 0.001);
    }
    
    @Test
    public void testMathFunctions() {
        MolangParser parser = new MolangParser(null);
        
        IValue result = parser.parseExpression("math.sin(90)");
        assertEquals(1.0, result.evalAsDouble(null), 0.001);
    }
    
    @Test
    public void testErrorHandling() {
        MolangParser parser = new MolangParser(null);
        
        // 测试语法错误
        IValue result = parser.parseExpression("1 + + 2");
        assertEquals(0.0, result.evalAsDouble(null), 0.001);
    }
}
```

### 2. 集成测试

#### 创建测试环境
```java
package cn.solarmoon.spark_core.molang.test;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.core.MolangParser;
import cn.solarmoon.spark_core.molang.core.storage.VariableStorage;
import net.minecraft.world.entity.player.Player;

public class MolangIntegrationTest {
    
    @Test
    public void testWithAnimatable() {
        // 创建模拟的可动画对象
        IAnimatable<Player> mockAnimatable = createMockAnimatable();
        
        MolangParser parser = new MolangParser(null);
        IValue expression = parser.parseExpression("q.health / q.max_health");
        
        double result = expression.evalAsDouble(mockAnimatable);
        assertTrue(result >= 0.0 && result <= 1.0);
    }
    
    private IAnimatable<Player> createMockAnimatable() {
        // 实现模拟对象创建逻辑
        return null; // 简化示例
    }
}
```

### 3. 性能测试

#### 基准测试
```java
package cn.solarmoon.spark_core.molang.test;

import cn.solarmoon.spark_core.molang.core.MolangParser;
import cn.solarmoon.spark_core.molang.core.value.IValue;
import org.junit.jupiter.api.Test;

public class MolangPerformanceTest {
    
    @Test
    public void benchmarkParsing() {
        MolangParser parser = new MolangParser(null);
        String expression = "math.sin(q.anim_time * 180) * math.cos(q.anim_time * 90)";
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < 10000; i++) {
            IValue result = parser.parseExpression(expression);
        }
        
        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        
        System.out.println("解析 10000 次表达式耗时: " + duration / 1_000_000 + " ms");
    }
    
    @Test
    public void benchmarkEvaluation() {
        MolangParser parser = new MolangParser(null);
        IValue expression = parser.parseExpression("math.sin(q.anim_time * 180) * math.cos(q.anim_time * 90)");
        
        IAnimatable<?> mockAnimatable = createMockAnimatable();
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < 100000; i++) {
            double result = expression.evalAsDouble(mockAnimatable);
        }
        
        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        
        System.out.println("评估 100000 次表达式耗时: " + duration / 1_000_000 + " ms");
    }
}
```

## 最佳实践

### 1. 代码组织
- 将相关功能分组到同一个包中
- 使用清晰的命名约定
- 添加详细的 JavaDoc 注释
- 遵循现有的代码风格

### 2. 性能优化
- 缓存频繁使用的表达式解析结果
- 使用 StringPool 优化字符串操作
- 避免在热路径中创建不必要的对象
- 合理使用变量类型（临时 vs 作用域）

### 3. 错误处理
- 提供有意义的错误消息
- 使用适当的异常类型
- 记录详细的调试信息
- 实现优雅的降级机制

### 4. 向后兼容性
- 保持现有 API 的稳定性
- 使用 @Deprecated 标记过时的方法
- 提供迁移指南
- 充分测试兼容性

### 5. 文档编写
- 为所有公共 API 编写文档
- 提供使用示例
- 说明性能特征
- 记录已知限制

## 常见问题和解决方案

### 1. 解析错误
**问题**: 表达式解析失败
**解决方案**: 
- 检查语法是否正确
- 验证函数名和参数
- 查看日志中的详细错误信息

### 2. 性能问题
**问题**: 表达式评估过慢
**解决方案**:
- 简化复杂表达式
- 缓存解析结果
- 使用更高效的数据结构

### 3. 内存泄漏
**问题**: 变量存储占用过多内存
**解决方案**:
- 及时清理不需要的变量
- 使用适当的变量生命周期
- 定期重置解析器状态

### 4. 扩展冲突
**问题**: 多个模组的扩展发生冲突
**解决方案**:
- 使用唯一的绑定前缀
- 检查名称冲突
- 实现优雅的错误处理

## 贡献指南

### 1. 提交代码
- Fork 项目仓库
- 创建功能分支
- 编写测试用例
- 提交 Pull Request

### 2. 代码审查
- 确保代码质量
- 验证测试覆盖率
- 检查文档完整性
- 评估性能影响

### 3. 发布流程
- 更新版本号
- 编写变更日志
- 运行完整测试套件
- 创建发布标签

## 总结

通过遵循本指南，开发者可以有效地扩展和维护 SparkCore 的 Molang 系统，为用户提供更强大的动画脚本功能。关键要点包括：

1. **理解架构**: 掌握 Molang 系统的核心组件和工作原理
2. **遵循规范**: 使用一致的代码风格和命名约定
3. **充分测试**: 编写全面的单元测试和集成测试
4. **性能优化**: 关注性能影响，使用最佳实践
5. **文档完善**: 为所有扩展提供清晰的文档

Molang 系统的模块化设计使得扩展变得相对简单，同时保持了系统的稳定性和性能。
