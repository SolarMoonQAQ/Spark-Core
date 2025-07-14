# SparkCore 资源依赖系统 - 快速参考

## 🚀 命令速查

| 命令 | 功能 | 示例 |
|------|------|------|
| `/spark deps list` | 依赖概览 | 显示总资源数、依赖数统计 |
| `/spark deps stats` | 详细统计 | 完整的依赖图统计信息 |
| `/spark deps check <resource>` | 检查依赖 | `/spark deps check spark_core:sword_anim` |
| `/spark deps tree <resource>` | 依赖树 | `/spark deps tree spark_core:player_model` |
| `/spark deps dependents <resource>` | 查看依赖者 | 哪些资源依赖于指定资源 |
| `/spark deps cascade <resource>` | 级联影响 | 卸载时会影响哪些资源 |
| `/spark deps validate <file>` | 验证文件 | `/spark deps validate "path/to/file.json"` |
| `/spark deps orphans <dir>` | 孤立文件 | `/spark deps orphans "sparkcore/"` |

## 📄 元数据文件模板

### 基础模板
```json
{
    "requires": [
        {
            "id": "namespace:resource_name",
            "type": "HARD|SOFT|OPTIONAL",
            "version": ">=1.0.0",
            "description": "依赖说明"
        }
    ],
    "provides": ["feature1", "feature2"],
    "tags": ["category", "type"],
    "properties": {
        "author": "作者名",
        "version": "1.0.0",
        "description": "资源描述"
    }
}
```

### 动画文件模板
```json
{
    "requires": [
        {
            "id": "spark_core:player_model",
            "type": "HARD",
            "description": "需要玩家模型骨骼结构"
        }
    ],
    "provides": ["animation_type"],
    "tags": ["animation", "category"],
    "properties": {
        "author": "您的名字",
        "animation_duration_ms": 1500,
        "target_bones": ["rightArm", "leftArm", "body"]
    }
}
```

### 模型文件模板
```json
{
    "requires": [],
    "provides": ["model_type", "skeleton_type"],
    "tags": ["model", "core"],
    "properties": {
        "bone_count": 10,
        "texture_size": [64, 64],
        "format_version": "1.12.0"
    }
}
```

## 🏷️ 依赖类型对比

| 类型 | 缺失时行为 | 版本冲突行为 | 日志级别 | 使用场景 |
|------|-----------|-------------|---------|---------|
| **HARD** | ❌ 阻止加载 | ❌ 阻止加载 | ERROR | 核心依赖 |
| **SOFT** | ⚠️ 继续加载 | ⚠️ 继续加载 | WARN | 增强功能 |
| **OPTIONAL** | ✅ 继续加载 | ✅ 继续加载 | DEBUG | 可选特性 |

## 🔢 版本约束语法

| 约束格式 | 含义 | 示例 |
|---------|------|------|
| `>=1.0.0` | 大于等于版本 | `>=2.1.0` |
| `<=2.0.0` | 小于等于版本 | `<=3.5.1` |
| `==1.5.0` | 精确匹配版本 | `==1.0.0` |
| `1.0.0` | 精确匹配（默认） | `2.0.0` |

## 📁 文件命名规范

| 资源文件 | 元数据文件 | ✅/❌ |
|---------|-----------|-------|
| `sword_anim.json` | `sword_anim.meta.json` | ✅ 正确 |
| `player_model.json` | `player_model.meta.json` | ✅ 正确 |
| `combat.json` | `combat_meta.json` | ❌ 缺少.meta |
| `test.json` | `test.metadata.json` | ❌ 后缀错误 |

## 🔧 常见问题快速解决

### 问题：资源未被识别
```bash
# 1. 检查文件命名
确保 .meta.json 文件名与资源文件匹配

# 2. 验证JSON格式
/spark deps validate "path/to/your_file.json"

# 3. 检查资源ID格式
使用 namespace:resource_name 格式
```

### 问题：循环依赖
```bash
# 检测循环依赖
/spark deps tree problematic_resource

# 解决方案：
# 1. 提取公共依赖到新资源
# 2. 降级其中一个为SOFT依赖
# 3. 移除非必要依赖
```

### 问题：版本冲突
```bash
# 检查版本约束
/spark deps check resource_with_version_issue

# 解决方案：
# 1. 更新资源到兼容版本
# 2. 放宽版本约束
# 3. 改为SOFT依赖降低影响
```

## 🎯 最佳实践清单

### ✅ 应该做的
- 使用语义化版本号
- 为依赖添加清晰描述
- 合理选择依赖类型
- 定期验证依赖状态
- 遵循命名规范

### ❌ 不应该做的
- 创建循环依赖
- 过度使用HARD依赖
- 忽略版本约束
- 使用特殊字符命名
- 创建无意义的依赖

## 📊 状态符号说明

| 符号 | 含义 | 上下文 |
|------|------|-------|
| ✅ / ✓ | 成功/正常 | 依赖满足、验证通过 |
| ⚠️ / ⚠ | 警告 | 软依赖缺失、版本不匹配 |
| ❌ / ✗ | 错误/失败 | 硬依赖缺失、加载失败 |
| 📦 | 资源 | 依赖树中的资源节点 |
| ├─ / └─ | 树结构 | 依赖关系层级 |

## 🧪 测试流程

### 新资源测试
```bash
# 1. 创建元数据文件
# 2. 验证格式
/spark deps validate "path/to/new_resource.json"

# 3. 检查依赖
/spark deps check namespace:new_resource

# 4. 查看依赖树
/spark deps tree namespace:new_resource

# 5. 测试级联影响
/spark deps cascade namespace:new_resource
```

### 更新现有资源
```bash
# 1. 备份原有配置
# 2. 更新元数据文件
# 3. 重新验证
/spark deps check namespace:updated_resource

# 4. 检查影响范围
/spark deps dependents namespace:updated_resource
```

## 🔍 调试技巧

### 查看日志
```bash
# 服务器日志中搜索以下关键词：
# - "依赖验证"
# - "DependencyGraph"
# - "ModelIndex依赖"
# - 资源ID名称
```

### 常用调试命令组合
```bash
# 全面检查特定资源
/spark deps check your_resource
/spark deps tree your_resource  
/spark deps dependents your_resource

# 系统健康检查
/spark deps stats
/spark deps orphans "sparkcore/"
```

---

## 📞 获取帮助

- **技术文档**: `docs/resource_dependency_system_technical.md`
- **用户指南**: `docs/resource_dependency_system_user_guide.md`
- **命令帮助**: 游戏内使用 `/spark deps` 查看可用命令
- **日志调试**: 查看服务器日志获取详细错误信息 