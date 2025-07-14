# SparkCore 资源级依赖系统 - 用户指南

## 📋 目录

1. [快速开始](#1-快速开始)
2. [资源ID与路径关系](#2-资源id与路径关系)
3. [元数据文件详解](#3-元数据文件详解)
4. [依赖类型说明](#4-依赖类型说明)
5. [命令行工具](#5-命令行工具)
6. [实用案例](#6-实用案例)
7. [常见问题](#7-常见问题)
8. [最佳实践](#8-最佳实践)

## 1. 快速开始

### 1.1 什么是资源级依赖系统？

SparkCore 资源级依赖系统允许您为游戏资源（动画、模型、纹理、IK约束等）定义依赖关系。这使得：

- ✅ **自动验证**: 系统自动检查资源依赖是否满足
- ✅ **智能管理**: 避免因依赖缺失导致的错误
- ✅ **热重载支持**: 安全地重载资源而不破坏依赖关系
- ✅ **版本控制**: 支持版本约束确保兼容性

### 1.2 第一个依赖文件

假设您有一个剑战斗动画文件 `fightskill_sword.json`，为它创建依赖元数据：

1. **创建元数据文件**: 在同一目录下创建 `fightskill_sword.meta.json`

2. **编写依赖配置**:
```json
{
    "requires": [
        {
            "id": "spark_core:player_model",
            "type": "HARD",
            "description": "需要玩家基础模型提供骨骼结构"
        },
        {
            "id": "spark_core:base_states",
            "type": "SOFT", 
            "description": "基础状态动画，提供战斗姿态参考"
        }
    ],
    "provides": [
        "sword_combat",
        "weapon_animations"
    ],
    "tags": ["combat", "weapon", "sword"],
    "properties": {
        "author": "您的名字",
        "description": "剑类武器战斗动画"
    }
}
```

3. **完成！** 系统将自动加载并验证这些依赖关系。

### 1.3 验证依赖

使用命令检查依赖状况：
```
/spark deps check spark_core:fightskill_sword
```

## 2. 资源ID与路径关系

### 2.1 资源ID生成规则

SparkCore 使用**多命名空间资源发现机制**，资源ID的生成遵循以下规则：

#### 基本格式
```
{namespace}:{resource_path}
```

#### 路径到ID的转换

**目录结构示例**:
```
run/
├── spark_core/                    # 命名空间目录
│   ├── animations/                # 资源类型目录
│   │   ├── player/               # 子目录
│   │   │   ├── idle.json         # 资源文件
│   │   │   └── walk.json
│   │   └── combat/
│   │       ├── sword_attack.json
│   │       └── magic_cast.json
│   └── models/
│       └── player_model.json
├── my_mod/                        # 自定义命名空间
│   └── animations/
│       └── special_moves.json
└── legacy_default/                # 兼容性命名空间
    └── animations/
        └── old_animation.json
```

**对应的资源ID**:
```
spark_core:player/idle         # run/spark_core/animations/player/idle.json
spark_core:player/walk         # run/spark_core/animations/player/walk.json
spark_core:combat/sword_attack # run/spark_core/animations/combat/sword_attack.json
spark_core:combat/magic_cast   # run/spark_core/animations/combat/magic_cast.json
spark_core:player_model        # run/spark_core/models/player_model.json
my_mod:special_moves           # run/my_mod/animations/special_moves.json
legacy_default:old_animation   # run/legacy_default/animations/old_animation.json
```

### 2.2 命名空间发现

系统会自动扫描以下位置的命名空间：

#### 2.2.1 游戏目录扫描
- **路径**: `run/{namespace}/`
- **要求**: 包含支持的资源类型目录（animations, models, textures等）

#### 2.2.2 模组Assets目录
- **路径**: `mods/{mod}.jar/assets/{namespace}/`
- **转换**: 自动识别模组中的资源

#### 2.2.3 Spark包文件
- **路径**: `*.spark` 包文件
- **格式**: 压缩的资源包，内部遵循相同的目录结构

### 2.3 资源类型支持

系统支持以下资源类型目录：

| 目录名 | 资源类型 | 示例文件 |
|--------|----------|----------|
| `animations` | 动画文件 | `sword_attack.json` |
| `models` | 模型文件 | `player_model.json` |
| `textures` | 纹理文件 | `player_skin.png` |
| `scripts` | 脚本文件 | `combat_logic.js` |
| `sounds` | 音效文件 | `sword_clash.ogg` |
| `ik_constraints` | IK约束 | `arm_ik.json` |
| `lang` | 语言文件 | `zh_cn.json` |
| `shaders` | 着色器 | `glow_effect.glsl` |

### 2.4 ID解析示例

#### 示例1: 深层目录结构
```
spark_core/animations/player/combat/weapons/sword/
├── basic_attack.json
├── combo_1.json
└── special_move.json
```

**生成的资源ID**:
```
spark_core:player/combat/weapons/sword/basic_attack
spark_core:player/combat/weapons/sword/combo_1
spark_core:player/combat/weapons/sword/special_move
```

#### 示例2: 模块化组织
```
my_combat_mod/
├── animations/
│   ├── melee/
│   │   ├── sword.json      # my_combat_mod:melee/sword
│   │   └── axe.json        # my_combat_mod:melee/axe
│   └── ranged/
│       ├── bow.json        # my_combat_mod:ranged/bow
│       └── crossbow.json   # my_combat_mod:ranged/crossbow
└── models/
    ├── weapons.json        # my_combat_mod:weapons
    └── armor.json          # my_combat_mod:armor
```

### 2.5 ID引用规则

在元数据文件中引用其他资源时，**必须使用完整的资源ID**：

#### ✅ 正确的引用方式
```json
{
    "requires": [
        {
            "id": "spark_core:player/base_model",
            "type": "HARD",
            "description": "基础玩家模型"
        },
        {
            "id": "my_mod:combat/sword_animations",
            "type": "SOFT",
            "description": "剑类动画集合"
        }
    ]
}
```

#### ❌ 错误的引用方式
```json
{
    "requires": [
        {
            "id": "player/base_model",           // 缺少命名空间
            "type": "HARD"
        },
        {
            "id": "combat/sword_animations",     // 缺少命名空间
            "type": "SOFT"
        }
    ]
}
```

### 2.6 路径查找优先级

当系统查找资源时，按以下优先级顺序：

1. **动态注册的资源** (最高优先级)
2. **游戏目录中的松散文件** (`run/{namespace}/`)
3. **模组Assets目录** (`assets/{namespace}/`)
4. **Spark包文件** (*.spark)
5. **兼容性回退** (`legacy_default` 命名空间)

## 3. 元数据文件详解

### 3.1 文件命名规则

- **命名格式**: `{资源文件名}.meta.json`
- **位置**: 与对应资源文件在同一目录
- **ID对应**: 元数据文件的ID与其对应资源文件的路径ID完全一致

**示例**:
```
spark_core/animations/player/combat/
├── sword_attack.json              # 资源文件
├── sword_attack.meta.json         # 元数据文件
├── magic_cast.json               # 资源ID: spark_core:player/combat/magic_cast
├── magic_cast.meta.json          # 对应的元数据
├── special/
│   ├── ultimate.json             # 资源ID: spark_core:player/combat/special/ultimate
│   └── ultimate.meta.json        # 对应的元数据
```

### 3.2 基础结构

```json
{
    "requires": [],     // 依赖列表
    "provides": [],     // 提供的特性
    "tags": [],         // 标签
    "properties": {}    // 自定义属性
}
```

### 3.3 字段说明

#### requires (依赖列表)
定义此资源需要的其他资源：

```json
"requires": [
    {
        "id": "spark_core:player/base_model",    // 完整的资源ID
        "type": "HARD",                          // 依赖类型
        "version": ">=1.0.0",                    // 版本约束（可选）
        "description": "描述为什么需要这个依赖"     // 说明（可选）
    }
]
```

#### provides (提供特性)
声明此资源提供的特性或接口：

```json
"provides": [
    "combat_animations",    // 提供战斗动画特性
    "weapon_support",       // 支持武器动画
    "player_animations"     // 玩家动画集合
]
```

#### tags (标签)
用于分类和搜索：

```json
"tags": [
    "combat",      // 战斗相关
    "weapon",      // 武器相关
    "animation",   // 动画类型
    "core"         // 核心资源
]
```

#### properties (自定义属性)
存储额外信息：

```json
"properties": {
    "author": "作者名",
    "version": "1.0.0",
    "category": "combat",
    "priority": 100,
    "estimated_duration_ms": 1500,
    "target_bones": ["rightArm", "leftArm", "body"]
}
```

## 4. 依赖类型说明

### 4.1 HARD (硬依赖)

**定义**: 必须存在的依赖，缺失时阻止资源加载

**使用场景**:
- 模型依赖：动画需要特定的骨骼结构
- 核心资源：基础功能模块
- 关键纹理：UI界面必需的图标

**示例**:
```json
{
    "id": "spark_core:player/base_model",
    "type": "HARD",
    "description": "动画需要玩家模型的骨骼结构"
}
```

### 4.2 SOFT (软依赖)

**定义**: 推荐存在的依赖，缺失时记录警告但继续加载

**使用场景**:
- 增强功能：提供额外特效的资源
- 优化资源：提升体验但非必需
- 兼容层：为其他模组提供的适配

**示例**:
```json
{
    "id": "spark_core:effects/particle_effects",
    "type": "SOFT", 
    "description": "提供战斗特效，提升视觉体验"
}
```

### 4.3 OPTIONAL (可选依赖)

**定义**: 完全可选的依赖，主要用于特性发现

**使用场景**:
- 第三方集成：可选的模组兼容
- 调试工具：开发时使用的辅助资源
- 实验功能：测试中的新特性

**示例**:
```json
{
    "id": "debug_mod:tools/combat_visualizer",
    "type": "OPTIONAL",
    "description": "调试时显示战斗判定框"
}
```

## 5. 命令行工具

### 5.1 基础命令

所有依赖相关命令都以 `/spark deps` 开头：

```bash
/spark deps <子命令> [参数]
```

### 5.2 命令详解

#### 📊 统计信息
```bash
/spark deps list    # 显示依赖概览
/spark deps stats   # 详细统计信息
```

**输出示例**:
```
=== 资源依赖概览 ===
• 总资源数: 15
• 总依赖数: 28  
• 硬依赖数: 12
• 软依赖数: 16
```

#### 🔍 依赖检查
```bash
/spark deps check <资源ID>
```

**示例**:
```bash
/spark deps check spark_core:player/combat/sword_attack
```

**输出示例**:
```
=== 检查资源依赖: spark_core:player/combat/sword_attack ===
✓ spark_core:player/base_model
⚠ spark_core:effects/particle_effects (SOFT): 资源不存在
✗ missing_mod:weapons/sword_model (HARD): 未找到命名空间 'missing_mod' 的注册表
```

#### 🌳 依赖树
```bash
/spark deps tree <资源ID>
```

**输出示例**:
```
=== 依赖树: spark_core:player/combat/sword_attack ===
📦 spark_core:player/combat/sword_attack
├─ spark_core:player/base_model
├─ spark_core:animations/base_states
│  └─ spark_core:animations/common_animations
└─ spark_core:animations/hand_animations
```

#### 👥 依赖者查询
```bash
/spark deps dependents <资源ID>
```

**输出示例**:
```
=== 依赖者: spark_core:player/base_model ===
• spark_core:player/combat/sword_attack
• spark_core:player/combat/hammer_attack  
• spark_core:animations/base_states
```

#### ⚠️ 级联影响
```bash
/spark deps cascade <资源ID>
```

**输出示例**:
```
=== 级联卸载预览: spark_core:player/base_model ===
警告：卸载此资源将同时卸载以下资源：
• spark_core:player/combat/sword_attack
• spark_core:animations/base_states
```

#### 🔧 文件验证
```bash
/spark deps validate <文件路径>
```

**示例**:
```bash
/spark deps validate "run/spark_core/animations/player/combat/sword_attack.json"
```

#### 🔍 孤立文件检测
```bash
/spark deps orphans <目录路径>
```

**示例**:
```bash
/spark deps orphans "run/spark_core/animations"
```

#### 📍 路径到ID转换
```bash
/spark deps resolve-path <文件路径>
```

**示例**:
```bash
/spark deps resolve-path "run/spark_core/animations/player/combat/sword_attack.json"
# 输出: spark_core:player/combat/sword_attack
```

#### 🔗 ID到路径转换
```bash
/spark deps resolve-id <资源ID>
```

**示例**:
```bash
/spark deps resolve-id "spark_core:player/combat/sword_attack"
# 输出: run/spark_core/animations/player/combat/sword_attack.json
```

## 6. 实用案例

### 6.1 案例1：创建战斗技能包

**场景**: 您正在制作一套完整的战斗技能动画包

**目录结构**:
```
spark_core/animations/player/combat/
├── base/
│   ├── stance.json
│   └── stance.meta.json
├── sword/
│   ├── basic_attack.json
│   ├── basic_attack.meta.json
│   ├── combo_attack.json
│   └── combo_attack.meta.json
└── effects/
    ├── particles.json
    └── particles.meta.json
```

**步骤**:

1. **基础动画** (`stance.meta.json`):
```json
{
    "requires": [
        {
            "id": "spark_core:player/base_model",
            "type": "HARD",
            "description": "基础玩家模型"
        }
    ],
    "provides": ["combat_base", "stance_animations"],
    "tags": ["combat", "base", "stance"],
    "properties": {
        "resource_id": "spark_core:player/combat/base/stance"
    }
}
```

2. **剑技能** (`basic_attack.meta.json`):
```json
{
    "requires": [
        {
            "id": "spark_core:player/combat/base/stance",
            "type": "HARD", 
            "description": "基础战斗姿态"
        },
        {
            "id": "spark_core:animations/weapon_handling",
            "type": "SOFT",
            "description": "武器操作动画"
        }
    ],
    "provides": ["sword_attacks", "melee_combat"],
    "tags": ["combat", "sword", "melee"],
    "properties": {
        "resource_id": "spark_core:player/combat/sword/basic_attack"
    }
}
```

3. **特效增强** (`particles.meta.json`):
```json
{
    "requires": [
        {
            "id": "spark_core:player/combat/sword/basic_attack",
            "type": "SOFT",
            "description": "剑技能动画"
        }
    ],
    "provides": ["combat_particles", "visual_effects"],
    "tags": ["effects", "particles", "enhancement"],
    "properties": {
        "resource_id": "spark_core:player/combat/effects/particles"
    }
}
```

### 6.2 案例2：跨命名空间依赖

**场景**: 您的模组需要依赖其他模组的资源

**目录结构**:
```
my_mod/animations/
├── special_moves.json
└── special_moves.meta.json

other_mod/models/
├── special_weapon.json
└── special_weapon.meta.json
```

**跨模组依赖配置** (`special_moves.meta.json`):
```json
{
    "requires": [
        {
            "id": "spark_core:player/base_model",
            "type": "HARD",
            "description": "基础玩家模型"
        },
        {
            "id": "other_mod:special_weapon", 
            "type": "HARD",
            "description": "特殊武器模型"
        },
    ],
    "provides": [
        "cross_mod_compatibility",
        "special_animations"
    ],
    "properties": {
        "resource_id": "my_mod:special_moves"
    }
}
```

### 6.3 案例3：版本管理和兼容性

**场景**: 确保动画包与特定版本的模组兼容

**版本约束** (`versioned_content.meta.json`):
```json
{
    "requires": [
        {
            "id": "spark_core:core/animation_system",
            "type": "HARD",
            "version": ">=2.0.0",
            "description": "需要 SparkCore 2.0 或更高版本"
        },
        {
            "id": "minecraft:player/base_model",
            "type": "HARD",
            "version": "==1.21.1",
            "description": "仅支持 Minecraft 1.21.1"
        }
    ],
    "properties": {
        "resource_id": "my_mod:versioned_content",
        "compatibility_matrix": {
            "spark_core": ">=2.0.0",
            "minecraft": "1.21.1",
            "neoforge": ">=20.1.0"
        }
    }
}
```

## 7. 常见问题

### 7.1 ❓ 为什么我的资源没有被识别？

**可能原因**:
1. 命名空间目录结构不正确
2. 元数据文件命名错误
3. JSON 格式有误
4. 资源ID格式不正确

**解决方法**:
```bash
# 检查目录结构
确保目录结构为: {namespace}/{resource_type}/{path}/file.json

# 验证元数据文件
/spark deps validate "路径/到/your_file.json"

# 检查ID解析
/spark deps resolve-path "路径/到/your_file.json"

# 检查文件命名
确保 your_file.meta.json 与 your_file.json 对应
```

### 7.2 ❓ 如何确定正确的资源ID？

**方法1：使用路径解析命令**
```bash
/spark deps resolve-path "run/spark_core/animations/player/combat/sword.json"
# 输出: spark_core:player/combat/sword
```

**方法2：查看资源列表**
```bash
/spark deps list
# 显示所有已发现的资源ID
```

**方法3：目录结构推算**
```
路径: run/{namespace}/{resource_type}/{sub_path}/{file}.json
ID:   {namespace}:{sub_path}/{file}

示例:
路径: run/my_mod/animations/player/special.json  
ID:   my_mod:player/special
```

### 7.3 ❓ 如何处理循环依赖？

**问题**: A依赖B，B依赖A

**解决方案**:
1. **重新设计**: 提取公共部分到第三个资源
2. **降级依赖**: 将其中一个改为SOFT或OPTIONAL
3. **移除依赖**: 如果不是真正必需的

**示例重构**:
```json
// 错误：循环依赖
// A.meta.json -> requires B  
// B.meta.json -> requires A

// 正确：提取公共部分
// common.meta.json (无依赖)
// A.meta.json -> requires common  
// B.meta.json -> requires common
```

### 7.4 ❓ 软依赖失败时如何处理？

软依赖失败是正常的，系统会：
1. 记录警告日志
2. 继续加载资源
3. 可能丢失某些增强功能

**检查影响**:
```bash
/spark deps check your_resource_id
```

### 7.5 ❓ 如何调试依赖问题？

**步骤**:
1. **检查命名空间发现**:
   ```bash
   /spark deps list
   # 查看是否发现了对应的命名空间
   ```

2. **检查资源ID解析**:
   ```bash
   /spark deps resolve-path "文件路径"
   /spark deps resolve-id "资源ID"
   ```

3. **检查依赖状态**:
   ```bash
   /spark deps check problematic_resource
   ```

4. **查看依赖树**:
   ```bash
   /spark deps tree problematic_resource
   ```

5. **验证文件**:
   ```bash
   /spark deps validate file_path
   ```

### 7.6 ❓ 版本约束语法

支持的版本约束格式：

| 约束 | 含义 | 示例 |
|------|------|------|
| `>=1.0.0` | 大于等于 | `>=2.1.0` |
| `<=2.0.0` | 小于等于 | `<=3.5.1` |
| `==1.5.0` | 精确匹配 | `==1.0.0` |
| `1.0.0` | 精确匹配（默认） | `2.0.0` |

## 8. 最佳实践

### 8.1 📝 命名规范

**命名空间命名**:
- 使用小写字母和下划线
- 避免与现有模组冲突
- 建议使用模组ID作为命名空间

**目录结构规范**:
```
{namespace}/
├── animations/          # 动画资源
├── models/             # 模型资源  
├── textures/           # 纹理资源
├── scripts/            # 脚本资源
├── sounds/             # 音效资源
├── ik_constraints/     # IK约束
├── lang/               # 语言文件
└── shaders/            # 着色器
```

**资源ID命名**:
- 使用 `namespace:path/resource_name` 格式
- 路径使用小写字母和下划线
- 避免特殊字符和空格

**示例**:
```
✅ 好的命名:
spark_core:player/base_model
my_mod:combat/sword_animations  
addon_pack:textures/weapon_icons

❌ 避免的命名:
SparkCore:Player/BaseModel (大写)
my-mod:combat-animations (连字符)
mod:animations with spaces (空格)
```

### 8.2 🏗️ 依赖设计原则

#### 最小依赖原则
只声明真正需要的依赖：
```json
// ❌ 过度依赖
"requires": [
    {"id": "mod:feature_a", "type": "HARD"},
    {"id": "mod:feature_b", "type": "HARD"},  // 实际只用到了 feature_a
    {"id": "mod:feature_c", "type": "HARD"}   // 完全没用到
]

// ✅ 合理依赖  
"requires": [
    {"id": "mod:feature_a", "type": "HARD"}
]
```

#### 合理选择依赖类型
```json
{
    "requires": [
        {
            "id": "core:essential_system",
            "type": "HARD",           // 核心功能 -> HARD
            "description": "核心系统，必须存在"
        },
        {
            "id": "enhancement:visual_effects", 
            "type": "SOFT",           // 增强功能 -> SOFT
            "description": "视觉效果增强，可选"
        },
        {
            "id": "debug:dev_tools",
            "type": "OPTIONAL",       // 调试工具 -> OPTIONAL
            "description": "开发调试工具"
        }
    ]
}
```

### 8.3 📁 目录组织策略

#### 按功能分组
```
my_combat_mod/
├── animations/
│   ├── melee/              # 近战动画
│   │   ├── sword/
│   │   ├── axe/
│   │   └── hammer/
│   ├── ranged/             # 远程动画
│   │   ├── bow/
│   │   └── crossbow/
│   └── magic/              # 魔法动画
│       ├── fire/
│       └── ice/
├── models/
│   ├── weapons/
│   └── armor/
└── textures/
    ├── weapons/
    └── armor/
```

#### 按优先级分层
```
spark_core/
├── animations/
│   ├── core/               # 核心动画（最高优先级）
│   │   ├── player_base.json
│   │   └── player_base.meta.json
│   ├── basic/              # 基础动画
│   │   ├── walk.json
│   │   └── walk.meta.json
│   └── advanced/           # 高级动画
│       ├── combat.json
│       └── combat.meta.json
```

### 8.4 📚 文档化

#### 添加详细描述
```json
{
    "requires": [
        {
            "id": "spark_core:player/base_model",
            "type": "HARD",
            "description": "需要玩家模型提供rightArm, leftArm, body等骨骼用于动画绑定"
        }
    ],
    "properties": {
        "description": "专为PvP战斗设计的剑技能动画包，包含5种基础攻击和3种连招",
        "author": "AnimationMaster",
        "version": "1.2.0",
        "resource_id": "spark_core:player/combat/sword_skills",
        "file_path": "run/spark_core/animations/player/combat/sword_skills.json",
        "compatibility": ["spark_core:>=2.0.0"],
        "usage_note": "需要在技能系统中配置对应的技能ID"
    }
}
```

### 8.5 🔄 版本管理

#### 语义化版本控制
```json
{
    "properties": {
        "version": "1.2.3",    // 主版本.次版本.修订版本
        "changelog": {
            "1.2.3": "修复动画播放时的骨骼错位问题",
            "1.2.0": "新增双手剑攻击动画",
            "1.1.0": "添加连招系统支持"
        }
    }
}
```

#### 兼容性声明
```json
{
    "requires": [
        {
            "id": "spark_core:core/animation_system",
            "version": ">=2.0.0",
            "description": "需要动画系统2.0的新特性"
        }
    ],
    "properties": {
        "min_spark_core_version": "2.0.0",
        "tested_versions": ["2.0.0", "2.1.0", "2.2.0"],
        "breaking_changes": {
            "2.0.0": "骨骼命名规范变更，需要更新动画文件"
        }
    }
}
```

### 8.6 ⚡ 性能优化

#### 分组加载
```json
// 核心包 - 最高优先级
{
    "properties": {
        "priority": 1000,        // 高优先级先加载
        "category": "core",
        "load_order": "early"
    }
}

// 扩展包 - 较低优先级  
{
    "properties": {
        "priority": 500,
        "category": "extension",
        "load_order": "normal"
    }
}
```

#### 懒加载标记
```json
{
    "properties": {
        "lazy_load": true,       // 标记为懒加载
        "load_trigger": "combat_start",  // 触发条件
        "unload_when_idle": true // 空闲时卸载
    }
}
```

### 8.7 🧪 测试和验证

#### 定期检查
```bash
# 检查所有资源的依赖状态
/spark deps stats

# 查找孤立的元数据文件
/spark deps orphans "run/spark_core/"

# 验证特定资源
/spark deps check spark_core:your_resource

# 检查路径解析
/spark deps resolve-path "run/spark_core/animations/test.json"
```

#### 集成测试
创建测试用的元数据文件来验证复杂依赖场景：

```json
// test_scenario.meta.json
{
    "requires": [
        {"id": "test:dependency_a", "type": "HARD"},
        {"id": "test:dependency_b", "type": "SOFT"},
        {"id": "test:dependency_c", "type": "OPTIONAL"}
    ],
    "properties": {
        "test_scenario": true,
        "resource_id": "test:scenario",
        "expected_behavior": "应该加载成功，记录软依赖警告"
    }
}
```

---

## 🎉 总结

资源级依赖系统为 SparkCore 带来了强大的资源管理能力。通过合理使用**多命名空间资源发现**、正确的**资源ID映射**和遵循最佳实践，您可以：

- 🛡️ **提高稳定性**: 避免因依赖缺失导致的错误
- 🚀 **提升开发效率**: 自动化的依赖检查和验证  
- 🔧 **简化维护**: 清晰的依赖关系便于长期维护
- 🤝 **增强兼容性**: 更好的模组间协作支持
- 📁 **灵活组织**: 支持多种目录结构和命名空间

### 关键要点提醒

1. **资源ID格式**: 始终使用 `{namespace}:{path}` 格式
2. **目录结构**: 遵循 `{namespace}/{resource_type}/{sub_path}/` 结构
3. **元数据文件**: 与资源文件同名，添加 `.meta.json` 后缀
4. **命令工具**: 善用 `/spark deps` 命令进行调试和验证

开始使用这个系统，让您的资源管理变得更加智能和可靠！

如有问题，请使用 `/spark deps` 命令进行诊断，或查看服务器日志获取详细错误信息。 