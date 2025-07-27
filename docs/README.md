# SparkCore 文档中心

欢迎来到 SparkCore 文档中心！这里包含了 SparkCore 的所有技术文档、开发指南和用户手册。

## 📁 文档目录结构

### 🏗️ 系统架构 (`system_architecture/`)

核心系统的架构设计和实现原理文档。

- **[资源系统架构概览](system_architecture/resource_system_overview.md)** - **（已更新）** 全新的图状资源管理系统架构。
- **[JavaScript 系统完整指南](system_architecture/javascript_system_complete_guide.md)** - **（已更新）** JS系统如何与新资源系统集成。
- **[模块系统与资源指南](system_architecture/architecture_and_new_resource_guide.md)** - **（已更新）** 模块系统设计与新资源创建指南。
- **[Molang 系统完整指南](system_architecture/molang_system_complete_guide.md)** - **（新）** Molang 表达式语言系统的完整架构和使用指南。

### 📚 API 参考 (`api_reference/`)

各种 API 接口的详细参考文档。

- **[SparkCustomnpcApi](api_reference/SparkCustomnpcApi.md)** - 原生 Java API 完整参考。
- **[JavaScript API 参考](api_reference/javascript_api_reference.md)** - JavaScript 脚本 API 文档。
- **[Molang API 参考](api_reference/molang_api_reference.md)** - **（新）** Molang 表达式语言 API 完整参考文档。

### 🛠️ 开发指南 (`development_guides/`)

面向开发者的实用开发指南和教程。

- **[资源创建与管理指南](development_guides/resource_creation_guide.md)** - **（新）** 如何在新系统中创建和管理资源。
- **[JS 热重载实现](development_guides/js_hotreload_implementation.md)** - **（已更新）** JS热重载在新架构下的工作原理。
- **[JS 运行时流程](development_guides/js_runtime_flow.md)** - **（已更新）** JS脚本从文件到执行的完整生命周期。
- **[JS 系统技术文档](development_guides/js_refactor_documentation.md)** - **（已更新）** JS系统的当前技术实现总结。
- **[Molang 开发指南](development_guides/molang_development_guide.md)** - **（新）** Molang 系统扩展和开发的完整指南。

### 🔧 技术细节 (`technical_details/`)

深入的技术实现细节和高级主题。

- **[资源依赖系统技术文档](technical_details/resource_dependency_system_technical.md)** - **（已更新）** 深入解析`ResourceGraphManager`及其辅助组件。
- **[动态注册表技术文档](technical_details/dynamic_registry.md)** - **（已更新）** `DynamicAwareRegistry`在资源系统中的核心作用。
- **[网络同步机制](technical_details/network_synchronization.md)** - 网络同步的技术实现。
- **[Molang 引擎技术实现](technical_details/molang_engine_implementation.md)** - **（新）** Molang 引擎的深度技术实现详解。

### 🚨 故障排除 (`troubleshooting/`)

常见问题解决方案和调试指南。

- **[常见问题解答](troubleshooting/issue_explanation.md)** - 常见问题的解释和解决方案。
- **[EntityHelper 方法签名修复](troubleshooting/entityhelper_method_signature_fixes.md)** - EntityHelper 相关修复说明。
- **[Molang 故障排除指南](troubleshooting/molang_troubleshooting_guide.md)** - **（新）** Molang 表达式常见问题和解决方案。

### 👥 用户指南 (`user_guides/`)

面向最终用户的使用指南和教程。

- **[Molang 用户指南](user_guides/molang_user_guide.md)** - **（新）** Molang 表达式语言的用户使用指南。

### 🌐 多语言文档

- **[依赖参考](依赖参考.md)** - 中文依赖系统参考。
- **[数据包参考](数据包参考.md)** - 中文数据包使用参考。

## 🚀 快速开始

### 新用户推荐阅读路径

#### 🔰 **初次使用者**
1. [资源系统架构概览](system_architecture/resource_system_overview.md) - 了解整体架构。
2. [资源创建与管理指南](development_guides/resource_creation_guide.md) - 学习基础使用。

#### 💻 **JavaScript 开发者**
1. [JavaScript 系统完整指南](system_architecture/javascript_system_complete_guide.md) - 理解 JS 执行环境。
2. [JS 运行时流程](development_guides/js_runtime_flow.md) - 了解运行机制。

#### 🎭 **动画脚本开发者**
1. [Molang 用户指南](user_guides/molang_user_guide.md) - 学习 Molang 表达式语言基础。
2. [Molang 系统完整指南](system_architecture/molang_system_complete_guide.md) - 深入理解 Molang 系统架构。

#### 🏗️ **模组开发者**
1. [模块系统与资源指南](system_architecture/architecture_and_new_resource_guide.md) - 学习模块系统概念。
2. [资源依赖系统技术文档](technical_details/resource_dependency_system_technical.md) - 深入技术实现。
3. [动态注册表技术文档](technical_details/dynamic_registry.md) - 高级资源管理。

#### 🔧 **系统维护者**
2. [网络同步机制](technical_details/network_synchronization.md) - 网络架构理解。

### 常用功能快速入门

#### 资源依赖管理
```bash
# 查看系统统计信息
/spark deps stats

# 验证特定资源的依赖
/spark deps check my_mod:player/jump

# 查看资源的依赖树
/spark deps tree my_mod:player/jump

# 查看哪些资源依赖于它
/spark deps dependents sparkcore:player_model
```

#### JavaScript 脚本开发
```javascript
// my_mod/scripts/skill/fireball.js
Skill.create("my_mod:fireball_skill", builder => {
    // 技能逻辑
});

// my_mod/scripts/skill/fireball.meta.json
{
  "requires": [
    { "id": "my_mod:animations/cast_fireball", "type": "HARD" }
  ]
}
```

#### Molang 表达式开发
```molang
// 基础数学运算
math.sin(q.anim_time * 180) * 0.5

// 条件逻辑
q.is_on_ground ? 'walk' : 'jump'

// 变量使用
v.health_ratio = q.health / q.max_health;
return v.health_ratio > 0.5 ? 1.0 : 0.0;

// 复杂动画控制
v.wave = math.sin(q.anim_time * 180) * math.cos(q.anim_time * 90);
v.breathing = math.sin(q.anim_time * 60) * 0.1 + 1.0;
return v.wave * v.breathing;
```

## 📖 文档使用指南

### 📋 文档类型说明

| 文档类型 | 目标读者 | 内容特点 | 示例 |
|---|---|---|---|
| **架构文档** | 开发者、架构师 | 系统设计、组件关系、技术决策 | [资源系统架构概览](system_architecture/resource_system_overview.md) |
| **API 参考** | 开发者 | 详细方法签名、参数说明、示例代码 | [SparkCustomnpcApi](api_reference/SparkCustomnpcApi.md) |
| **开发指南** | 内容创作者、脚本开发者 | 实用教程、最佳实践、案例分析 | [资源创建与管理指南](development_guides/resource_creation_guide.md) |
| **技术细节** | 高级开发者 | 深入实现原理、高级配置、扩展开发 | [资源依赖系统技术文档](technical_details/resource_dependency_system_technical.md) |

---

## 🎉 开始使用

选择适合您的起点，开始探索 SparkCore 的强大功能：

- 🚀 **[快速上手](development_guides/resource_creation_guide.md)** - 立即开始使用
- 📖 **[系统概览](system_architecture/resource_system_overview.md)** - 理解整体架构
- 💻 **[JavaScript 开发](system_architecture/javascript_system_complete_guide.md)** - 开始脚本编程
- 🔧 **[高级功能](technical_details/)** - 探索深度特性

**祝您使用愉快！** 🎉