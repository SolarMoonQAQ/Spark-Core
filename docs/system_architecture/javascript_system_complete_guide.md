# SparkCore JavaScript 系统完整指南

## 1. 系统架构概览

SparkCore 的 JavaScript 系统基于 Mozilla Rhino 引擎构建，提供了一个强大的脚本执行环境。在新架构下，它与核心资源系统紧密集成，支持动态脚本加载、热重载和网络同步。

### 1.1 核心组件

#### SparkJS 基类系统
```kotlin
abstract class SparkJS {
    val context = Context.enter()      // Rhino JavaScript 上下文
    val scope = context.initStandardObjects()  // 标准对象作用域
    
    // 核心方法
    open fun loadAllFromRegistry()
    open fun reloadScript(script: OJSScript)
    open fun unloadScript(apiId: String, fileName: String)
    protected open fun executeScript(script: OJSScript)
}
```

#### 服务端与客户端实现
- **ServerSparkJS**: 服务端脚本执行，支持线程安全和网络同步。
- **ClientSparkJS**: 客户端脚本执行，在渲染线程执行。

#### JSApi 接口系统
```kotlin
interface JSApi {
    val id: String // API 模块唯一标识 (如 'skill', 'animation')
    
    fun onLoad()    // 脚本加载完成后调用
    fun onReload()  // 脚本重载时调用
}
```

#### 脚本数据模型
```kotlin
data class OJSScript(
    val apiId: String,              // API模块ID
    val fileName: String,           // 脚本文件名
    val content: String,            // 脚本内容
    val location: ResourceLocation  // 资源位置标识
)
```

### 1.2 数据流架构

JS脚本现在是资源图的一部分，其加载和管理完全融入了新的资源系统。

```mermaid
graph TD
    subgraph "File System & Hot Reload"
        A["run/sparkcore/my_mod/my_mod/scripts/skill/my_skill.js"] -- File Event --> B[ResHotReloadService]
    end

    subgraph "Resource System"
        C[JavaScriptHandler] -- addOrUpdateResource() --> D[ResourceGraphManager]
        D -- "Calculates dependencies, etc." --> E[ResourceNode for my_skill.js]
    end
    
    subgraph "JS Engine & Registries"
        F[SparkRegistries.JS_SCRIPTS] -- Notifies --> G[ServerSparkJS]
        G -- Executes Script --> H[Rhino Context]
        H -- Calls --> I[JSApi (e.g., JSSkillApi)]
    end
    
    subgraph "Network Sync"
        J[JSIncrementalSyncS2CPacket]
    end

    B --> C
    C -- "Registers OJSscript" --> F
    F -- "onDynamicRegister callback" --> J
    G -- "reloadScript()" --> H
    J -- "Sends to client" --> K[Client]

```

## 2. 运行时执行流程

### 2.1 系统初始化序列

1.  **资源发现**: `ResourceDiscoveryService` 扫描四层目录结构 `run/sparkcore/{modId}/{moduleName}/`，发现 `scripts` 资源类型。
2.  **处理器初始化**: `HandlerDiscoveryService` 实例化 `JavaScriptHandler`。
3.  **初始扫描**: `JavaScriptHandler.initialize()` 被调用，它会：
    -   扫描所有已发现的 `scripts` 目录下的 `.js` 文件。
    -   对每个文件调用 `onResourceAdded()`。
4.  **图节点创建**: 在 `onResourceAdded()` 内部，`ResourceGraphManager.addOrUpdateResource()` 被调用。
    -   `ResourcePathResolver` 将文件路径转换为 `ResourceLocation` (如 `my_mod:my_mod/scripts/skill/my_skill`)。
    -   `ResourceNode` 在资源图中被创建。
    -   `DependencyCalculator` 根据 `.meta.json` 文件或默认规则计算脚本的依赖。
5.  **脚本注册**: `JavaScriptHandler` 读取脚本内容，创建一个 `OJSScript` 对象，并将其注册到 `SparkRegistries.JS_SCRIPTS` 动态注册表中。
6.  **服务端执行**: `SparkJsApplier.onLevelLoad` 触发 `ServerSparkJS.loadAllFromRegistry()`，从注册表中获取所有脚本并首次执行它们。
7.  **客户端同步**: `JSScriptDataSendingTask` 在玩家登录时，将所有脚本通过全量同步包 `JSScriptDataSyncPayload` 发送给客户端，客户端接收后执行脚本。

### 2.2 服务端执行策略

**线程安全执行机制**:
```kotlin
override fun executeScript(script: OJSScript) {
    val server = ServerLifecycleHooks.getCurrentServer()
    if (server != null) {
        // 确保在服务器主线程执行
        server.execute {
            super.executeScript(script)
        }
    } else {
        // 如果服务器未启动，直接执行（启动阶段）
        super.executeScript(script)
    }
}
```

### 2.3 客户端执行策略

**渲染线程执行**:
```kotlin
override fun executeScript(script: OJSScript) {
    val minecraft = Minecraft.getInstance()
    if (minecraft.isSameThread) {
        // 已在渲染线程，直接执行
        super.executeScript(script)
    } else {
        // 切换到渲染线程执行
        minecraft.execute {
            super.executeScript(script)
        }
    }
}
```

## 3. 热重载机制

### 3.1 文件监控实现

`ResHotReloadService` 监控脚本文件目录的变更。当文件被修改、创建或删除时，会通知 `JavaScriptHandler`。

**监控目录结构** (四层结构):
```
run/sparkcore/
├── {modId}/
│   ├── {moduleName}/
│   │   ├── scripts/
│   │   │   ├── {api_id}/
│   │   │   │   ├── script1.js
│   │   │   │   ├── script2.js
│   │   │   │   └── ...
│   │   │   └── ...
│   │   └── ...
│   └── ...
└── ...
```

**示例**:
```
run/sparkcore/
├── my_mod/
│   ├── my_mod/
│   │   └── scripts/
│   │       ├── skill/
│   │       │   ├── fireball.js
│   │       │   └── heal.js
│   │       └── ik/
│   │           └── player_ik.js
│   └── combat_system/
│       └── scripts/
│           └── skill/
│               └── combo_attack.js
└── spark_core/
    └── sparkcore/
        └── scripts/
            ├── skill/
            │   └── test.js
            └── resource_path/
                └── path_helper.js
```

### 3.2 API ID提取逻辑

`JavaScriptHandler` 使用 `extractApiIdFromPath()` 方法从脚本的 ResourceLocation 中提取 API ID，支持四层目录结构：

**提取规则**：
1. **四层结构优先**: 从路径中查找 `/scripts/` 或 `/script/`，提取其后的第一个目录名作为 API ID
2. **传统格式兼容**: 支持以 `scripts/` 或 `script/` 开头的传统路径格式
3. **默认回退**: 如果无法提取，默认使用 `skill` API

**示例**：
| ResourceLocation | 提取的API ID | 说明 |
|---|---|---|
| `my_mod:my_mod/scripts/skill/fireball` | `skill` | 四层结构，从 `/scripts/` 后提取 |
| `spark_core:sparkcore/scripts/ik/player_ik` | `ik` | 四层结构，从 `/scripts/` 后提取 |
| `my_mod:my_mod/scripts/resource_path/helper` | `resource_path` | 四层结构，从 `/scripts/` 后提取 |
| `old_mod:scripts/skill/legacy` | `skill` | 传统格式兼容 |

**代码实现**：
```kotlin
private fun extractApiIdFromPath(location: ResourceLocation): String {
    val path = location.path

    // 处理四层目录结构: {moduleName}/scripts/{api_id}/{fileName}
    if (path.contains("/scripts/")) {
        val pathParts = path.split("/")
        val scriptsIndex = pathParts.indexOf("scripts")
        if (scriptsIndex >= 0 && scriptsIndex + 1 < pathParts.size) {
            return pathParts[scriptsIndex + 1]
        }
    }

    // 其他格式处理...
    return "skill" // 默认值
}
```

### 3.3 增量同步协议

#### 增量同步数据包
```kotlin
data class JSIncrementalSyncS2CPacket(
    val apiId: String,
    val fileName: String,
    val operationType: ChangeType,  // ADDED, MODIFIED, REMOVED
    val scriptContent: String
)
```

#### 同步流程
1.  **文件变化检测** → `ResHotReloadService`
2.  **处理器响应** → `JavaScriptHandler.onResourceModified()`
3.  **资源图更新** → `ResourceGraphManager.addOrUpdateResource()`
4.  **注册表更新** → `SparkRegistries.JS_SCRIPTS` 更新 `OJSScript`
5.  **服务端重载** → `ServerSparkJS.reloadScript()` 被调用，重新执行脚本
6.  **网络同步** → 注册表的回调触发，发送增量同步包到客户端
7.  **客户端处理** → 客户端接收包，更新本地注册表，并调用 `ClientSparkJS.reloadScript()`

## 4. 网络同步详解

### 4.1 全量同步机制（玩家登录）

**配置阶段同步**:
`JSScriptDataSendingTask` 在玩家登录时，会从`SparkRegistries.JS_SCRIPTS`获取所有脚本，打包成`JSScriptDataSyncPayload`，一次性发送给客户端。

**客户端处理**:
客户端收到全量包后，会清空本地的动态脚本注册表，然后注册所有接收到的脚本，最后调用 `ClientSparkJS.instance.loadAllFromRegistry()` 来执行它们。

### 4.2 增量同步机制（运行时更新）

**服务端触发**:
`DynamicAwareRegistry` 在其条目被更新时，会触发 `onDynamicRegister` 回调，该回调负责创建并发送 `JSIncrementalSyncS2CPacket`。

**客户端接收**:
客户端的 `JSIncrementalSyncS2CPacket` 处理器会根据包中的操作类型（`ADDED`, `MODIFIED`, `REMOVED`）来更新本地的注册表，并调用 `ClientSparkJS` 中相应的方法（`reloadScript` 或 `unloadScript`）来应用变更。

## 5. 开发指南

### 5.1 创建自定义 JSApi

**步骤1: 实现 JSApi 接口**
```kotlin
class CustomJSApi : JSApi {
    override val id: String = "custom"
    
    override fun onLoad() {
        // 处理脚本加载完成后的逻辑
    }
    
    override fun onReload() {
        // 处理脚本重载时的清理逻辑
    }
}
```

**步骤2: 注册 JSApi**
通过`@EventBusSubscriber`监听`SparkJSRegisterEvent`来注册你的API。

**步骤3: 创建脚本目录**
```
run/sparkcore/my_mod/my_mod/scripts/custom/
├── init.js
└── ...
```

### 5.2 脚本编写最佳实践

#### 基本脚本结构
```javascript
// ResourceID: my_mod:my_mod/scripts/custom/example

// 1. 全局函数定义
function customFunction() { /* ... */ }

// 2. 注册自定义内容
// (调用你自定义的JSApi暴露的方法)
CustomApi.register("example", { /* ... */ });
```

#### 错误处理
```javascript
try {
    riskyOperation();
} catch (e) {
    Logger.error("脚本执行出错: " + e.message);
}
```

#### 与原生 Java/Kotlin 交互
```javascript
// 访问 Java 类
var JavaClass = Java.type("java.util.ArrayList");
var list = new JavaClass();

// 调用 SparkCore API
var SparkCore = Java.type("cn.solarmoon.spark_core.SparkCore");
SparkCore.LOGGER.info("来自JavaScript的消息");
```

### 5.3 调试与故障排除

#### 启用调试日志
在 `sparkcore.properties` 或相关日志配置文件中启用DEBUG级别日志。

#### 常见问题诊断

**1. 脚本未加载**
- 检查文件是否在正确的 `run/sparkcore/{modId}/{moduleName}/scripts/{api_id}/` 目录下。
- 确认 JSApi 已正确注册。
- 查看 `debug.log` 中 `JavaScriptHandler` 和 `ResourceGraphManager` 的日志。

**2. 网络同步失败**
- 检查服务端与客户端版本一致性。
- 确认网络连接正常。
- 查看与 `JS...Sync...Packet` 相关的日志。

**3. 线程安全问题**
新架构通过在 `ServerSparkJS` 和 `ClientSparkJS` 中切换线程，已基本解决此问题。确保你的脚本中没有长时间的阻塞操作。

## 6. 技术细节

### 6.1 数据访问

`OJSScript.get(resourceLocation)` 等方法现在会优先从 `DynamicAwareRegistry` 中查找资源，如果找不到，再回退到静态的 `ORIGINS` 映射。这保证了动态加载的资源总能被正确访问。

### 6.2 线程安全设计

**服务端线程安全**: 所有脚本执行都通过 `server.execute()` 提交到服务器主线程。
**客户端线程安全**: 所有脚本执行都通过 `minecraft.execute()` 提交到渲染线程。

### 6.3 内存管理

脚本的生命周期与它在 `DynamicAwareRegistry` 中的注册状态绑定。当脚本从注册表中移除时（例如，文件被删除），`JavaScriptHandler` 会调用 `unloadScript` 来清理其在JS引擎中的状态，防止内存泄漏。

## 7. 结论

SparkCore 的 JavaScript 系统通过与新资源系统深度集成，提供了一个强大、稳定且可扩展的脚本执行环境。通过统一的架构设计、线程安全的执行机制和智能的网络同步，为开发者提供了高效的脚本开发和部署体验。新的动态注册表系统确保了数据一致性和系统可靠性，同时保持了良好的向后兼容性。