# Molang 用户指南

## 什么是 Molang？

Molang（Motion Language）是 SparkCore 中用于动画和表达式计算的脚本语言。它允许您创建动态的动画效果、条件逻辑和复杂的数学计算，为您的模组提供强大的动画控制能力。

## 基础语法

### 数字和运算符

#### 基本数字
```molang
42          // 整数
3.14        // 小数
-5          // 负数
0.5         // 小于1的小数
```

#### 算术运算符
```molang
1 + 2       // 加法，结果：3
5 - 3       // 减法，结果：2
4 * 2       // 乘法，结果：8
10 / 2      // 除法，结果：5
7 % 3       // 取模，结果：1
```

#### 比较运算符
```molang
5 > 3       // 大于，结果：true
2 < 4       // 小于，结果：true
5 >= 5      // 大于等于，结果：true
3 <= 2      // 小于等于，结果：false
4 == 4      // 等于，结果：true
3 != 5      // 不等于，结果：true
```

#### 逻辑运算符
```molang
true && false   // 逻辑与，结果：false
true || false   // 逻辑或，结果：true
!true          // 逻辑非，结果：false
```

### 字符串
```molang
'hello'         // 字符串字面量
'world'         // 另一个字符串
'hello world'   // 包含空格的字符串
```

### 布尔值
```molang
true        // 真值
false       // 假值
```

## 变量系统

### 变量类型

#### 临时变量（t. 或 temp.）
临时变量在动画结束后自动清除，适用于存储临时计算结果。

```molang
t.my_temp = 5;                    // 设置临时变量
temp.calculation = 1 + 2 * 3;     // 存储计算结果
return t.my_temp * 2;             // 使用临时变量
```

#### 作用域变量（v. 或 variable.）
作用域变量在动画过程中持续存在，适用于存储动画状态。

```molang
v.health_ratio = q.health / q.max_health;  // 计算生命值比例
variable.is_low_health = v.health_ratio < 0.3;  // 判断是否低血量
return v.health_ratio;
```

#### 外置变量（c. 或 context.）
外置变量由外部系统提供，用于访问上下文信息。

```molang
c.custom_property      // 访问自定义属性
context.mod_data       // 访问模组数据
```

### 变量使用示例

```molang
// 计算并存储复杂表达式
v.angle = q.anim_time * 180;
v.wave = math.sin(v.angle) * 0.5;

// 使用条件逻辑
v.is_moving = q.ground_speed > 0.1;
return v.is_moving ? v.wave : 0;
```

## 内置函数

### 数学函数（math.）

#### 基础数学
```molang
math.abs(-5)           // 绝对值，结果：5
math.floor(3.7)        // 向下取整，结果：3
math.ceil(3.2)         // 向上取整，结果：4
math.round(3.6)        // 四舍五入，结果：4
math.trunc(-3.7)       // 截断，结果：-3
```

#### 比较函数
```molang
math.min(3, 7)         // 最小值，结果：3
math.max(3, 7)         // 最大值，结果：7
math.clamp(5, 0, 3)    // 限制范围，结果：3
```

#### 高级数学
```molang
math.sqrt(16)          // 平方根，结果：4
math.pow(2, 3)         // 幂运算，结果：8
math.exp(1)            // 指数函数，结果：2.718...
math.ln(math.e)        // 自然对数，结果：1
```

#### 三角函数（角度制）
```molang
math.sin(90)           // 正弦，结果：1
math.cos(0)            // 余弦，结果：1
math.tan(45)           // 正切，结果：1
math.asin(1)           // 反正弦，结果：90
math.acos(0)           // 反余弦，结果：90
math.atan(1)           // 反正切，结果：45
```

#### 实用工具
```molang
math.lerp(0, 10, 0.5)              // 线性插值，结果：5
math.lerprotate(0, 360, 0.25)      // 旋转插值，结果：90
math.random_integer(1, 6)          // 随机整数（1-6）
math.die_roll(6, 2)                // 投掷2个6面骰子
```

### 查询函数（q. 或 query.）

#### 时间相关
```molang
q.anim_time            // 当前动画时间
q.time_of_day          // 一天中的时间（0-1）
q.moon_phase           // 月相（0-7）
```

#### 实体状态
```molang
q.is_on_ground         // 是否在地面上
q.is_in_water          // 是否在水中
q.is_on_fire           // 是否着火
q.has_rider            // 是否有骑手
q.is_riding            // 是否在骑乘
```

#### 实体属性
```molang
q.health               // 当前生命值
q.max_health           // 最大生命值
q.ground_speed         // 地面移动速度
q.vertical_speed       // 垂直移动速度
q.head_x_rotation      // 头部X轴旋转
q.head_y_rotation      // 头部Y轴旋转
```

#### 位置信息
```molang
q.position(0)          // X坐标
q.position(1)          // Y坐标
q.position(2)          // Z坐标
q.distance_from_camera // 距离摄像机的距离
```

#### 物品相关
```molang
q.max_durability('mainhand')           // 主手物品最大耐久
q.remaining_durability('mainhand')     // 主手物品剩余耐久
q.equipped_item_any_tag('head', 'minecraft:helmets')  // 头盔是否有指定标签
```

## 实际应用示例

### 1. 基于生命值的动画

```molang
// 根据生命值比例调整动画速度
v.health_ratio = q.health / q.max_health;
v.speed_multiplier = math.lerp(0.5, 1.0, v.health_ratio);
return v.speed_multiplier;
```

### 2. 呼吸效果

```molang
// 创建平滑的呼吸动画
v.breath_cycle = math.sin(q.anim_time * 60) * 0.1 + 1.0;
return v.breath_cycle;
```

### 3. 行走动画控制

```molang
// 根据移动速度控制行走动画
v.is_moving = q.ground_speed > 0.1;
v.walk_speed = math.clamp(q.ground_speed * 2, 0, 2);
return v.is_moving ? v.walk_speed : 0;
```

### 4. 受伤闪烁效果

```molang
// 受伤时的闪烁效果
v.hurt_flash = q.hurt_time > 0 ? math.sin(q.anim_time * 720) : 0;
v.flash_intensity = math.clamp(v.hurt_flash, 0, 1);
return v.flash_intensity;
```

### 5. 条件动画切换

```molang
// 根据多个条件选择动画
v.is_in_combat = q.hurt_time > 0 || q.is_using_item;
v.is_sneaking = q.is_sneaking;
v.is_moving = q.ground_speed > 0.1;

// 优先级：战斗 > 潜行 > 移动 > 待机
return v.is_in_combat ? 'combat' : 
       v.is_sneaking ? 'sneak' : 
       v.is_moving ? 'walk' : 'idle';
```

### 6. 复杂的波浪动画

```molang
// 创建复杂的波浪效果
v.time = q.anim_time * 180;
v.wave1 = math.sin(v.time) * 0.3;
v.wave2 = math.sin(v.time * 1.5 + 90) * 0.2;
v.wave3 = math.sin(v.time * 0.7 + 45) * 0.1;
v.combined_wave = v.wave1 + v.wave2 + v.wave3;
return v.combined_wave;
```

## 性能优化技巧

### 1. 缓存复杂计算

```molang
// 不好的做法：重复计算
math.sin(q.anim_time * 180) + math.cos(q.anim_time * 180);

// 好的做法：缓存结果
v.angle = q.anim_time * 180;
v.sin_value = math.sin(v.angle);
v.cos_value = math.cos(v.angle);
return v.sin_value + v.cos_value;
```

### 2. 使用适当的变量类型

```molang
// 临时计算使用临时变量
t.temp_calc = expensive_calculation();

// 持久状态使用作用域变量
v.animation_state = determine_state();
```

### 3. 避免不必要的函数调用

```molang
// 不好的做法：每次都调用
q.health / q.max_health > 0.5 ? action1() : action2();

// 好的做法：缓存结果
v.health_ratio = q.health / q.max_health;
return v.health_ratio > 0.5 ? action1() : action2();
```

## 调试技巧

### 1. 使用调试输出

```molang
// 使用 debug_output 函数输出调试信息
debug_output('Health ratio: ' + (q.health / q.max_health));
```

### 2. 分步验证

```molang
// 将复杂表达式分解为多个步骤
v.step1 = q.health / q.max_health;
v.step2 = v.step1 * 2;
v.step3 = math.clamp(v.step2, 0, 1);
return v.step3;
```

### 3. 使用简单的测试值

```molang
// 使用固定值测试逻辑
v.test_value = 0.5;  // 替代复杂的查询
return v.test_value > 0.3 ? 1 : 0;
```

## 常见错误和解决方案

### 1. 语法错误

**错误**: `1 + + 2`
**解决**: `1 + 2`

**错误**: `math.sin(90`
**解决**: `math.sin(90)`

### 2. 类型错误

**错误**: `'hello' + 5`
**解决**: 确保操作数类型匹配

### 3. 除零错误

**错误**: `10 / 0`
**解决**: `q.max_health > 0 ? q.health / q.max_health : 0`

### 4. 变量未定义

**错误**: 使用未设置的变量
**解决**: 在使用前先设置变量值

## 最佳实践

### 1. 命名约定
- 使用有意义的变量名
- 使用下划线分隔单词
- 避免使用保留字

### 2. 代码组织
- 将相关计算分组
- 使用注释说明复杂逻辑
- 保持表达式简洁

### 3. 性能考虑
- 缓存重复计算
- 使用适当的变量类型
- 避免深度嵌套

### 4. 错误处理
- 检查除零情况
- 验证输入范围
- 提供默认值

## 总结

Molang 是一个强大而灵活的表达式语言，通过掌握其基础语法、变量系统和内置函数，您可以创建复杂而动态的动画效果。记住要：

1. **从简单开始**: 先掌握基础语法和常用函数
2. **逐步复杂化**: 逐渐添加更复杂的逻辑
3. **注重性能**: 优化表达式以获得更好的性能
4. **充分测试**: 验证表达式在各种情况下的行为
5. **保持整洁**: 使用清晰的命名和组织结构

通过实践和实验，您将能够充分利用 Molang 的强大功能来创建令人印象深刻的动画效果。
