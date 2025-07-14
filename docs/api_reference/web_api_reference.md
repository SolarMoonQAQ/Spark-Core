# SparkCore Web API 参考

## 1. 概述

### 1.1 什么是 SparkCore Web API？

SparkCore Web API 是一个基于 RESTful 架构的 HTTP 服务，允许外部应用程序通过标准的 HTTP 请求与 SparkCore 系统进行交互。该 API 提供了动画控制、模型管理、资源查询、日志搜索等功能，使开发者能够构建 Web 界面、移动应用或其他工具来管理和控制 SparkCore。

### 1.2 技术架构

**核心技术栈**：
- **Ktor 3.2.1** - 现代化的 Kotlin Web 框架
- **CIO 引擎** - 基于协程的 I/O，避免与 NeoForge 的 Netty 冲突
- **Gson** - JSON 序列化和反序列化
- **CORS 支持** - 跨域资源共享，支持 Web 前端集成

**架构特点**：
- **线程安全** - 使用 Kotlin 协程处理并发请求
- **统一响应格式** - 所有 API 返回标准的 `ApiResponse` 格式
- **错误处理** - 完整的异常捕获和堆栈跟踪
- **配置驱动** - 通过配置文件控制服务器行为

### 1.3 服务器配置

**配置文件位置**: `config/spark_core-common.toml`

```toml
[webServer]
    # 是否启用 Web 服务器
    enableWebServer = true
    # Web 服务器端口
    webServerPort = 8081
    # 是否启用 CORS 跨域支持
    webServerCorsEnabled = true
```

## 2. API 响应格式

### 2.1 统一响应结构

所有 API 端点都返回统一的 JSON 响应格式：

```json
{
    "success": true,
    "data": "响应数据",
    "message": "操作描述",
    "timestamp": "2025-07-14T04:47:52.665898600Z"
}
```

**字段说明**：
- `success` (boolean): 操作是否成功
- `data` (any): 响应数据，类型根据具体 API 而定
- `message` (string): 操作结果的描述信息
- `timestamp` (string): 响应时间戳 (ISO 8601 格式)

### 2.2 错误响应格式

当发生错误时，响应包含详细的错误信息：

```json
{
    "success": false,
    "data": {
        "type": "RuntimeException",
        "message": "具体错误信息",
        "stackTrace": "完整的堆栈跟踪..."
    },
    "message": "操作失败的描述",
    "timestamp": "2025-07-14T04:47:52.665898600Z"
}
```

### 2.3 HTTP 状态码

| 状态码 | 含义 | 使用场景 |
|--------|------|----------|
| 200 | OK | 操作成功 |
| 400 | Bad Request | 请求参数错误 |
| 500 | Internal Server Error | 服务器内部错误 |
| 503 | Service Unavailable | 服务器环境不可用 |

## 3. 基础端点

### 3.1 健康检查

**端点**: `GET /health`

**描述**: 检查 Web 服务器是否正常运行

**响应示例**:
```json
{
    "success": true,
    "data": "OK",
    "message": "Web服务器运行正常",
    "timestamp": "2025-07-14T04:47:52.665898600Z"
}
```

### 3.2 API 信息

**端点**: `GET /api/v1/`

**描述**: 获取 API 版本信息和可用端点列表

**响应示例**:
```json
{
    "success": true,
    "data": {
        "name": "SparkCore Web API",
        "version": "1.0.0",
        "status": "running",
        "endpoints": [
            "/api/v1/health",
            "/api/v1/animation/play",
            "/api/v1/animation/blend",
            "/api/v1/animation/replace-state",
            "/api/v1/model/load",
            "/api/v1/resources/list",
            "/api/v1/resources/registries",
            "/api/v1/debug/info",
            "/api/v1/logs/search"
        ]
    },
    "message": "API服务正常运行"
}
```

## 4. 动画控制 API

### 4.1 播放动画

**端点**: `POST /api/v1/animation/play`

**描述**: 播放指定的动画

**请求体**:
```json
{
    "name": "idle",
    "transTime": 1000,
    "entityId": 123
}
```

**参数说明**:
- `name` (string, 必需): 动画名称
- `transTime` (integer, 可选): 过渡时间（毫秒），默认为 0
- `entityId` (integer, 可选): 目标实体 ID，如果不提供则使用当前玩家

**响应示例**:
```json
{
    "success": true,
    "data": true,
    "message": "动画播放成功"
}
```

### 4.2 混合动画

**端点**: `POST /api/v1/animation/blend`

**描述**: 混合两个动画

**请求体**:
```json
{
    "anim1": "idle",
    "anim2": "walk",
    "weight": 0.5,
    "entityId": 123
}
```

**参数说明**:
- `anim1` (string, 必需): 第一个动画名称
- `anim2` (string, 必需): 第二个动画名称
- `weight` (number, 必需): 混合权重 (0.0 - 1.0)
- `entityId` (integer, 可选): 目标实体 ID

### 4.3 替换状态动画

**端点**: `POST /api/v1/animation/replace-state`

**描述**: 替换实体的状态动画

**请求体**:
```json
{
    "state": "idle",
    "animation": "custom_idle",
    "entityId": 123
}
```

**参数说明**:
- `state` (string, 必需): 状态名称
- `animation` (string, 必需): 新的动画名称
- `entityId` (integer, 可选): 目标实体 ID

## 5. 模型管理 API

### 5.1 加载模型

**端点**: `POST /api/v1/model/load`

**描述**: 为实体加载新的模型

**请求体**:
```json
{
    "path": "spark_core:custom_model",
    "entityId": 123
}
```

**参数说明**:
- `path` (string, 必需): 模型资源路径
- `entityId` (integer, 可选): 目标实体 ID

**响应示例**:
```json
{
    "success": true,
    "data": true,
    "message": "模型加载成功"
}
```

## 6. 资源查询 API

### 6.1 资源列表

**端点**: `GET /api/v1/resources/list`

**描述**: 获取所有已注册的资源列表

**响应示例**:
```json
{
    "success": true,
    "data": {
        "animations": [
            "spark_core:animations/player/base_state/state.sit",
            "spark_core:animations/player/base_state/state.land.move_back"
        ],
        "models": [
            "spark_core:models/player/custom_player"
        ],
        "textures": [
            "spark_core:textures/entity/custom_texture"
        ],
        "scripts": [
            "spark_core:scripts/skill/custom_skill"
        ],
        "ik_components": [
            "spark_core:ik/arm_constraint"
        ]
    },
    "message": "资源列表获取成功"
}
```

### 6.2 注册表状态

**端点**: `GET /api/v1/resources/registries`

**描述**: 获取各个注册表的状态信息

**响应示例**:
```json
{
    "success": true,
    "data": {
        "typed_animation": {
            "total_entries": 25,
            "registry_key": "spark_core:typed_animation",
            "value_type": "TypedAnimation",
            "is_empty": false
        },
        "models": {
            "total_entries": 12,
            "registry_key": "spark_core:models",
            "value_type": "OModel",
            "is_empty": false
        }
    },
    "message": "注册表状态获取成功"
}
```

## 7. 日志管理 API

### 7.1 搜索日志

**端点**: `POST /api/v1/logs/search`

**描述**: 根据条件搜索日志条目

**请求体**:
```json
{
    "level": "INFO",
    "side": "CLIENT",
    "regex": ".*API.*",
    "maxLines": 100,
    "maxChars": 50000
}
```

**参数说明**:
- `level` (string, 可选): 日志级别过滤 (TRACE, DEBUG, INFO, WARN, ERROR)
- `side` (string, 可选): 运行环境过滤 (CLIENT, SERVER)
- `regex` (string, 可选): 正则表达式搜索模式
- `maxLines` (integer, 可选): 最大返回行数，默认 1000
- `maxChars` (integer, 可选): 最大返回字符数，默认 100000

**响应示例**:
```json
{
    "success": true,
    "data": [
        {
            "id": 1,
            "timestamp": "2025-07-14T04:42:10.662229900Z",
            "level": "INFO",
            "side": "CLIENT",
            "logger": "SparkCore",
            "message": "Web服务器启动成功",
            "thread": "Server thread",
            "exception": null
        }
    ],
    "message": "日志搜索完成，找到 1 条记录"
}
```

### 7.2 日志统计

**端点**: `GET /api/v1/logs/stats`

**描述**: 获取日志统计信息

**响应示例**:
```json
{
    "success": true,
    "data": {
        "total_entries": 156,
        "max_entries": 10000,
        "current_side": "CLIENT",
        "level_counts": {
            "INFO": 120,
            "WARN": 25,
            "ERROR": 8,
            "DEBUG": 3
        },
        "side_counts": {
            "CLIENT": 156
        },
        "oldest_entry": "2025-07-14T04:42:10.662229900Z",
        "newest_entry": "2025-07-14T04:47:52.665898600Z"
    },
    "message": "日志统计信息获取成功"
}
```

### 7.3 获取最新日志

**端点**: `GET /api/v1/logs/recent?maxLines=50&maxChars=10000`

**描述**: 获取最新的日志条目

**查询参数**:
- `maxLines` (integer, 可选): 最大返回行数，默认 100
- `maxChars` (integer, 可选): 最大返回字符数，默认 50000

### 7.4 清空日志

**端点**: `DELETE /api/v1/logs/clear`

**描述**: 清空所有收集的日志

**响应示例**:
```json
{
    "success": true,
    "data": true,
    "message": "日志已清空"
}
```

## 8. 调试信息 API

### 8.1 系统信息

**端点**: `GET /api/v1/debug/info`

**描述**: 获取系统运行信息

**响应示例**:
```json
{
    "success": true,
    "data": {
        "server_status": "running",
        "web_server_port": 8081,
        "cors_enabled": true,
        "timestamp": 1720934872665,
        "java_version": "21.0.3",
        "java_vendor": "Eclipse Adoptium",
        "os_name": "Windows 11",
        "os_version": "10.0",
        "available_processors": 16,
        "max_memory": 4294967296,
        "total_memory": 1073741824,
        "free_memory": 536870912,
        "used_memory": 536870912,
        "spark_core_version": "spark_core",
        "graalvm_enabled": false
    },
    "message": "系统信息获取成功"
}
```

### 8.2 配置信息

**端点**: `GET /api/v1/debug/config`

**描述**: 获取当前配置信息

**响应示例**:
```json
{
    "success": true,
    "data": {
        "web_server": {
            "enabled": true,
            "port": 8081,
            "cors_enabled": true
        },
        "javascript": {
            "graalvm_enabled": false
        }
    },
    "message": "配置信息获取成功"
}
```

### 8.3 测试错误处理

**端点**: `GET /api/v1/debug/test-error`

**描述**: 故意触发错误以测试错误处理机制

**响应示例** (错误情况):
```json
{
    "success": false,
    "data": {
        "type": "RuntimeException",
        "message": "这是一个测试异常，用于验证错误堆栈信息功能",
        "stackTrace": "java.lang.RuntimeException: 这是一个测试异常...\n\tat ..."
    },
    "message": "测试错误处理功能"
}
```

### 8.4 垃圾回收

**端点**: `GET /api/v1/debug/gc`

**描述**: 执行垃圾回收并返回内存使用情况

**响应示例**:
```json
{
    "success": true,
    "data": {
        "before_gc": {
            "used_memory_before": 536870912,
            "free_memory_before": 536870912,
            "total_memory_before": 1073741824
        },
        "after_gc": {
            "used_memory_after": 268435456,
            "free_memory_after": 805306368,
            "total_memory_after": 1073741824,
            "gc_time_ms": 45
        },
        "memory_freed": 268435456
    },
    "message": "垃圾回收执行完成"
}
```
