# 资源创建与管理指南

## 1. 概述

本指南详细介绍如何在 SparkCore 的新资源系统中创建、管理和定义各种类型的资源，包括动画、模型、纹理、脚本和IK约束。新系统基于图状依赖管理，支持多命名空间、热重载和自动依赖验证。

### 1.1 新资源系统核心理念

- **路径即身份**: 资源的位置（路径）决定了其唯一的资源ID（`ResourceLocation`）。
- **元数据驱动**: 资源的依赖、特性和属性通过旁边的`.meta.json`文件定义。
- **命名空间**: 资源被组织在不同的命名空间下，便于模块化管理和避免冲突。
- **自动化**: 系统会自动发现资源、计算常规依赖并验证其完整性。

---

## 2. 资源组织与命名规范

### 2.1 目录结构

SparkCore采用**四层目录结构**来组织资源，格式为：`run/sparkcore/{modId}/{moduleName}/{resourceType}/`

**标准目录结构**:
```
run/sparkcore/
├── my_mod/                        # modId: my_mod
│   ├── my_mod/                    # moduleName: my_mod (主模块)
│   │   ├── animations/            # 动画资源
│   │   ├── models/                # 模型资源
│   │   ├── textures/              # 纹理资源
│   │   ├── scripts/               # JavaScript脚本
│   │   └── ik_constraints/        # IK约束
│   └── combat_system/             # moduleName: combat_system (战斗系统模块)
│       ├── animations/
│       └── scripts/
│
├── another_mod/                   # modId: another_mod
│   ├── another_mod/               # moduleName: another_mod (主模块)
│   │   ├── animations/
│   │   └── models/
│   └── magic_system/              # moduleName: magic_system (魔法系统模块)
│       └── scripts/
│
└── spark_core/                    # modId: spark_core (SparkCore核心)
    ├── sparkcore/                 # moduleName: sparkcore (核心模块)
    │   ├── animations/
    │   ├── models/
    │   ├── textures/
    │   ├── scripts/
    │   └── ik_constraints/
    └── combat/                    # moduleName: combat (战斗模块)
        ├── animations/
        └── scripts/
```

### 2.2 资源ID的生成规则

资源ID由系统根据四层目录结构自动生成，格式为 `{modId}:{moduleName}/{resourceType}/{path}`。

- **`{modId}`**: 模组标识符，对应第一层目录名（如 `my_mod`, `spark_core`）
- **`{moduleName}`**: 模块名称，对应第二层目录名（如 `my_mod`, `combat_system`）
- **`{resourceType}`**: 资源类型，对应第三层目录名（如 `animations`, `models`）
- **`{path}`**: 从第四层开始到**不包含扩展名**的文件名为止的相对路径

**四层结构示例**:
| 文件物理路径 | 生成的资源ID |
|---|---|
| `run/sparkcore/my_mod/my_mod/animations/player/combat/sword_attack.json` | `my_mod:my_mod/animations/player/combat/sword_attack` |
| `run/sparkcore/my_mod/combat_system/animations/sword/combo.json` | `my_mod:combat_system/animations/sword/combo` |
| `run/sparkcore/spark_core/sparkcore/models/player.json` | `spark_core:sparkcore/models/player` |
| `run/sparkcore/spark_core/sparkcore/textures/gui/icons.png` | `spark_core:sparkcore/textures/gui/icons` |
| `run/sparkcore/another_mod/magic_system/scripts/fireball.js` | `another_mod:magic_system/scripts/fireball` |

> **重要**: 在元数据文件中引用其他资源时，**必须**使用这个由系统生成的完整资源ID。

### 2.3 模块标识格式

模块使用以下格式进行标识：`{modId}:{moduleName}`

**示例**:
- `my_mod:my_mod` - MyMod的主模块
- `my_mod:combat_system` - MyMod的战斗系统模块
- `spark_core:sparkcore` - SparkCore的核心模块
- `spark_core:combat` - SparkCore的战斗模块

### 2.4 从旧格式迁移

如果您之前使用的是两层目录结构，请按以下步骤迁移：

**旧格式** (两层):
```
run/my_mod/animations/player.json
```
ResourceID: `my_mod:player`

**新格式** (四层):
```
run/sparkcore/my_mod/my_mod/animations/player.json
```
ResourceID: `my_mod:my_mod/animations/player`

**迁移步骤**:
1. 在`run/sparkcore/`下创建您的`{modId}/`目录
2. 在`{modId}/`下创建`{moduleName}/`目录（通常与modId相同）
3. 将原有的资源类型目录移动到新的模块目录下
4. 更新所有资源引用以使用新的ResourceLocation格式
5. 利用`SparkResourcePathBuilder`工具类生成标准路径

**向后兼容性**: 系统仍支持旧的两层格式，但建议迁移到四层结构以获得更好的模块化支持。

---

## 3. 元数据文件 (`.meta.json`)

每个资源都可以有一个配对的元数据文件，用于定义其依赖、特性和属性。

- **命名**: `{资源文件名}.meta.json` (例如 `sword_attack.json` 对应 `sword_attack.meta.json`)
- **位置**: 与其描述的资源文件放在同一目录下。
- **作用**: 为`ResourceGraphManager`提供构建依赖图所需的信息。

### 3.1 基础结构
```json
{
  "requires": [
    {
      "id": "namespace:dependency_id",
      "type": "HARD",
      "version": ">=1.0.0",
      "description": "依赖说明"
    }
  ],
  "provides": ["feature_name"],
  "tags": ["category_tag"],
  "properties": {
    "author": "Your Name",
    "version": "1.0.0"
  }
}
```

### 3.2 `requires` - 声明依赖

这是最重要的部分，用于声明该资源需要哪些其他资源才能正常工作。

- **`id` (必需)**: 依赖的完整资源ID。
- **`type` (必需)**: 依赖类型 (`HARD`, `SOFT`, `OPTIONAL`)。
- **`version` (可选)**: 版本约束。
- **`description` (可选)**: 对该依赖的描述。

**依赖类型说明**:
| 类型 | 缺失时行为 | 适用场景 |
|---|---|---|
| **HARD** | ❌ **加载失败** | 核心功能，缺少则无法工作（如动画依赖模型骨骼）|
| **SOFT** | ⚠️ **警告** | 推荐功能，缺少不影响核心（如模型依赖可选的特效贴图）|
| **OPTIONAL** | ✅ **忽略** | 完全可选的功能，用于发现与集成（如支持某个调试工具）|

### 3.3 其他字段

- **`provides`**: `List<String>` - 声明此资源提供的“虚拟特性”或“接口”，供其他资源依赖。
- **`tags`**: `List<String>` - 为资源打上标签，便于搜索和分类。
- **`properties`**: `Map<String, Any>` - 开放的键值对，用于存储任意自定义信息（如作者、版本、描述等）。

---

## 4. 创建各类资源 - 示例

### 4.1 动画 (`animations`)

**文件**: `run/sparkcore/my_mod/my_mod/animations/player/jump.json`

```json
{
  "format_version": "1.12.0",
  "animations": {
    "animation.player.jump": {
      "animation_length": 0.5,
      "bones": {
        "body": { "position": { "0.0": [0,0,0], "0.25": [0,5,0], "0.5": [0,0,0] } }
      }
    }
  }
}
```

**元数据**: `run/sparkcore/my_mod/my_mod/animations/player/jump.meta.json`

```json
{
  "requires": [
    {
      "id": "spark_core:sparkcore/models/player",
      "type": "HARD",
      "description": "跳跃动画需要玩家模型的基础骨骼。"
    }
  ],
  "tags": ["animation", "player", "movement"],
  "properties": {
    "author": "Anim-Master",
    "description": "一个基础的玩家跳跃动画。"
  }
}
```

### 4.2 模型 (`models`)

**文件**: `run/sparkcore/my_mod/my_mod/models/items/magic_sword.json`

```json
{
  "format_version": "1.12.0",
  "minecraft:geometry": [
    {
      "description": {
        "identifier": "geometry.magic_sword",
        "texture_width": 16,
        "texture_height": 16
      },
      "bones": [
        { "name": "root", "pivot": [0, 8, 0] },
        { "name": "handle", "parent": "root", "pivot": [0, 8, 0], "cubes": [...] },
        { "name": "blade", "parent": "handle", "pivot": [0, 16, 0], "cubes": [...] }
      ]
    }
  ]
}
```

**元数据**: `run/sparkcore/my_mod/my_mod/models/items/magic_sword.meta.json`

```json
{
  "requires": [
    {
      "id": "my_mod:my_mod/textures/items/magic_sword_texture",
      "type": "SOFT",
      "description": "魔法剑的默认贴图，可以被覆盖。"
    }
  ],
  "provides": ["magic_sword_model"],
  "tags": ["model", "weapon", "sword"],
  "properties": { "version": "1.1" }
}
```

### 4.3 JavaScript 脚本 (`scripts`)

**文件**: `run/sparkcore/my_mod/my_mod/scripts/skills/fireball.js`

```javascript
// ResourceID: my_mod:my_mod/scripts/skills/fireball
Skill.create("my_mod:fireball_skill", builder => {
    // ...
});
```

**元数据**: `run/sparkcore/my_mod/my_mod/scripts/skills/fireball.meta.json`

```json
{
  "requires": [
    {
      "id": "my_mod:my_mod/animations/magic/cast_fireball",
      "type": "HARD",
      "description": "火球术需要一个施法动画。"
    },
    {
      "id": "spark_core:sparkcore/effects/fire_particles",
      "type": "SOFT",
      "description": "可选的火焰粒子效果。"
    }
  ],
  "tags": ["script", "skill", "magic"]
}
```

---

## 5. 验证与调试

当你保存或修改资源文件后，系统会自动热重载并验证。你可以使用命令行工具来主动调试。

### 5.1 检查资源状态
检查特定资源的所有依赖是否都已满足。

```bash
/spark deps check my_mod:player/combat/sword_attack
```
**输出示例**:
```
=== 检查资源依赖: my_mod:player/combat/sword_attack ===
✓ HARD: sparkcore:player_model
⚠ SOFT: sparkcore:effects/sword_slash (资源不存在)
```

### 5.2 查看依赖树
可视化一个资源的完整依赖链。

```bash
/spark deps tree my_mod:player/combat/sword_attack
```
**输出示例**:
```
=== 依赖树: my_mod:player/combat/sword_attack ===
📦 my_mod:player/combat/sword_attack
├─ ✓ HARD: sparkcore:player_model
└─ ⚠ SOFT: sparkcore:effects/sword_slash
```

---

## 6. 总结

新的资源创建流程更加结构化和强大：

1.  **组织文件**: 将资源文件按`run/sparkcore/{modId}/{moduleName}/{resourceType}/`四层结构放置。
2.  **创建资源**: 编写你的模型、动画、脚本等文件。
3.  **定义元数据**: 创建对应的`.meta.json`文件，在`requires`中声明所有依赖。
4.  **验证**: 使用`/spark deps check`等命令确保一切正常。

通过遵循这个流程，你可以充分利用SparkCore强大的资源管理能力，构建稳定且易于维护的模组内容。