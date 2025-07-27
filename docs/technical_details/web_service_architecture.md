# SparkCore Web 服务架构技术文档

## 1. 概述

### 1.1 架构设计目标

SparkCore Web 服务旨在提供一个现代化、高性能、易于扩展的 RESTful API 接口，使外部应用程序能够与 SparkCore 系统进行交互。设计遵循以下原则：

- **线程安全**: 支持并发请求处理
- **模块化**: 清晰的分层架构和职责分离
- **可扩展**: 易于添加新的 API 端点和功能
- **兼容性**: 避免与 NeoForge 框架的冲突
- **可观测性**: 完整的日志记录和错误追踪

### 1.2 技术选型

| 组件 | 技术 | 版本 | 选择原因 |
|------|------|------|----------|
| Web 框架 | Ktor | 3.2.1 | Kotlin 原生，协程支持，轻量级 |
| 服务器引擎 | CIO | - | 避免与 NeoForge Netty 冲突 |
| JSON 序列化 | Gson | - | 与项目现有技术栈一致 |
| 并发模型 | Kotlin 协程 | - | 高性能异步处理 |
| 日志系统 | SLF4J + Logback | - | 标准日志接口 |

## 2. 系统架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                    外部客户端                                │
│  (Web 前端, 移动应用, 第三方工具)                           │
└─────────────────────┬───────────────────────────────────────┘
                      │ HTTP/HTTPS
                      │
┌─────────────────────▼───────────────────────────────────────┐
│                 Ktor Web 服务器                             │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────────┐ │
│  │    CORS     │ │   JSON      │ │      路由管理           │ │
│  │   中间件    │ │  序列化     │ │   (Routing DSL)         │ │
│  └─────────────┘ └─────────────┘ └─────────────────────────┘ │
└─────────────────────┬───────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────┐
│                   服务层                                    │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐ │
│  │ ResourceApiService│ │  LogCollector   │ │   其他服务      │ │
│  │  (动画/模型)     │ │   (日志管理)    │ │                 │ │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘ │
└─────────────────────┬───────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────┐
│                SparkCore 核心系统                           │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────────┐ │
│  │  动画系统   │ │  模型系统   │ │     资源注册表          │ │
│  └─────────────┘ └─────────────┘ └─────────────────────────┘ │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────────┐ │
│  │  JS 系统    │ │  物理系统   │ │    Minecraft 集成       │ │
│  └─────────────┘ └─────────────┘ └─────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 核心组件

#### 2.2.1 KtorWebServer

**位置**: `cn.solarmoon.spark_core.web.KtorWebServer`

Web 服务的核心控制器，负责：
- 服务器生命周期管理（启动/停止）
- 中间件配置（CORS, JSON 序列化）
- 路由定义和请求分发
- 错误处理和响应格式化

```kotlin
object KtorWebServer {
    private var server: EmbeddedServer<*, *>? = null
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    fun start() { /* 启动逻辑 */ }
    fun stop() { /* 停止逻辑 */ }
    fun isRunning(): Boolean { /* 状态检查 */ }
}
```

#### 2.2.2 ResourceApiService

**位置**: `cn.solarmoon.spark_core.web.service.ResourceApiService`

资源操作的业务逻辑层，提供：
- 动画播放和混合控制
- 模型加载和切换
- 状态动画管理
- 与 SparkCore 核心系统的集成

```kotlin
object ResourceApiService {
    fun playAnimation(request: AnimationPlayRequest, level: ServerLevel, player: ServerPlayer): ApiResponse<Boolean>
    fun blendAnimations(request: AnimationBlendRequest, level: ServerLevel, player: ServerPlayer): ApiResponse<Boolean>
    fun loadModel(request: ModelLoadRequest, level: ServerLevel, player: ServerPlayer): ApiResponse<Boolean>
    fun replaceStateAnimation(request: AnimationReplaceStateRequest, level: ServerLevel, player: ServerPlayer): ApiResponse<Boolean>
}
```

#### 2.2.3 日志系统

**SparkLogger**: `cn.solarmoon.spark_core.web.logging.SparkLogger`
- 包装原始 SLF4J Logger
- 自动收集日志到 LogCollector
- 提供统一的搜索接口

**LogCollector**: `cn.solarmoon.spark_core.web.logging.LogCollector`
- 线程安全的日志存储
- 支持多条件搜索和过滤
- 内存管理和容量限制

## 3. 请求处理流程

### 3.1 典型请求流程

```
1. 客户端发送 HTTP 请求
   ↓
2. Ktor 接收请求并应用中间件
   ↓
3. 路由匹配和参数解析
   ↓
4. 请求体反序列化 (JSON → DTO)
   ↓
5. 业务逻辑处理 (Service 层)
   ↓
6. 与 SparkCore 核心系统交互
   ↓
7. 响应数据序列化 (DTO → JSON)
   ↓
8. 返回 HTTP 响应
```

### 3.2 错误处理机制

```kotlin
try {
    // 业务逻辑处理
    val result = service.processRequest(request)
    call.respond(HttpStatusCode.OK, ApiResponse.success(result))
} catch (e: Exception) {
    SparkCore.LOGGER.error("API调用失败", e)
    val errorResponse = createErrorResponse("操作失败", e)
    call.respond(HttpStatusCode.InternalServerError, errorResponse)
}
```

**错误响应包含**:
- 异常类型和消息
- 完整的堆栈跟踪
- 时间戳和上下文信息

### 3.3 服务器环境获取

```kotlin
private fun getServerLevel(): ServerLevel? {
    val server = ServerLifecycleHooks.getCurrentServer()
    return server?.overworld()
}

private fun getServerPlayer(): ServerPlayer? {
    val server = ServerLifecycleHooks.getCurrentServer()
    return server?.playerList?.players?.firstOrNull()
}
```

## 4. 数据传输对象 (DTO)

### 4.1 请求 DTO

**位置**: `cn.solarmoon.spark_core.web.dto`

```kotlin
// 动画播放请求
data class AnimationPlayRequest(
    val name: String,
    val transTime: Int = 0,
    val entityId: Int? = null
)

// 动画混合请求
data class AnimationBlendRequest(
    val anim1: String,
    val anim2: String,
    val weight: Double,
    val entityId: Int? = null
)

// 模型加载请求
data class ModelLoadRequest(
    val path: String,
    val entityId: Int? = null
)
```

### 4.2 响应 DTO

```kotlin
data class ApiResponse<T>(
    val success: Boolean,
    val data: T,
    val message: String,
    val timestamp: String = Instant.now().toString()
) {
    companion object {
        fun <T> success(data: T, message: String = "操作成功"): ApiResponse<T>
        fun <T> error(message: String): ApiResponse<T>
    }
}
```

## 5. 配置管理

### 5.1 配置结构

Web 服务的配置集成到 SparkCore 的统一配置系统中：

```kotlin
// SparkConfig.COMMON_CONFIG
val enableWebServer: ConfigValue<Boolean>
val webServerPort: ConfigValue<Int>
val webServerCorsEnabled: ConfigValue<Boolean>
```

### 5.2 配置文件示例

```toml
[webServer]
    enableWebServer = true
    webServerPort = 8081
    webServerCorsEnabled = true
```

## 6. 线程安全和并发

### 6.1 协程使用

```kotlin
// 服务器启动在独立的协程作用域中
private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

serverScope.launch {
    server = embeddedServer(CIO, port = port) {
        configureServer(corsEnabled)
    }
    server?.start(wait = false)
}
```

### 6.2 线程安全保证

- **LogCollector**: 使用 `ConcurrentLinkedQueue` 和原子操作
- **ResourceApiService**: 委托给 SparkCore 的线程安全组件
- **配置访问**: 通过 SparkConfig 的线程安全接口

## 7. 性能优化

### 7.1 内存管理

- **日志条目限制**: 最大保存 10,000 条日志
- **响应大小限制**: 支持 maxLines 和 maxChars 参数
- **连接池**: CIO 引擎的内置连接管理

### 7.2 缓存策略

- **资源列表缓存**: 动态注册表提供实时数据
- **配置缓存**: SparkConfig 的内置缓存机制

## 8. 监控和可观测性

### 8.1 日志记录

```kotlin
// 请求日志
SparkCore.LOGGER.info("API请求：播放动画 - name=${request.name}, entityId=${request.entityId}")

// 错误日志
SparkCore.LOGGER.error("API调用失败：playAnimation", e)
```

### 8.2 健康检查

- **基础健康检查**: `/health` 端点
- **详细系统信息**: `/api/v1/debug/info` 端点
- **配置状态**: `/api/v1/debug/config` 端点

### 8.3 错误追踪

- 完整的异常堆栈跟踪
- 请求上下文信息
- 时间戳和操作标识
