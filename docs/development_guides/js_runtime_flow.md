# SparkCore JavaScript 运行时流程

## 1. 概述

本文档详细描述了 SparkCore 中 JavaScript 脚本从文件系统到游戏内执行的完整运行时流程。该流程在新架构下由资源图系统（`ResourceGraphManager`）和热重载服务（`ResHotReloadService`）驱动。

## 2. 核心架构交互图

```mermaid
sequenceDiagram
    participant FS as File System
    participant HRS as ResHotReloadService
    participant JSH as JavaScriptHandler
    participant RGM as ResourceGraphManager
    participant REG as SparkRegistries.JS_SCRIPTS
    participant SJS as ServerSparkJS
    participant NET as Network Sync
    participant CJS as ClientSparkJS

    Note over FS, CJS: Initial Load / Player Join
    HRS ->> JSH: initialize()
    JSH ->> FS: Scans for all .js files
    JSH ->> RGM: addOrUpdateResource(file) for each script
    RGM ->> REG: Registers all OJSScript
    REG ->> SJS: Server executes all scripts via loadAllFromRegistry()
    
    SJS ->> NET: Sends JSScriptDataSyncPayload (Full Sync) to new Client
    NET ->> CJS: Receives Full Sync
    CJS ->> REG: Clears and re-registers all scripts
    CJS ->> CJS: Executes all scripts via loadAllFromRegistry()

    Note over FS, CJS: Hot Reload (File Change)
    FS ->> HRS: Detects file change (e.g., my_skill.js modified)
    HRS ->> JSH: onResourceModified(file)
    JSH ->> RGM: addOrUpdateResource(file)
    RGM ->> REG: Updates OJSScript in registry
    
    REG -- onDynamicRegister callback --> NET: Creates JSIncrementalSyncS2CPacket
    REG -- onDynamicRegister callback --> SJS: reloadScript(script)
    SJS ->> SJS: Re-evaluates the modified script
    
    NET ->> CJS: Receives Incremental Sync Packet
    CJS ->> REG: Updates single OJSScript in registry
    CJS ->> CJS: reloadScript(script)
    CJS ->> CJS: Re-evaluates the script on render thread

```

## 3. 详细流程分解

### 3.1 阶段一：服务器启动与初始加载

1.  **资源发现**:
    -   `ResourceDiscoveryService` 扫描所有模块路径（`sparkcore/`, `mods/` 等），识别出包含 `scripts` 的命名空间。

2.  **处理器初始化**:
    -   `HandlerDiscoveryService` 创建 `JavaScriptHandler` 实例。

3.  **初始扫描**:
    -   `JavaScriptHandler.initialize()` 遍历所有发现的 `scripts` 目录。
    -   对于每个 `.js` 文件，调用 `onResourceAdded()`。

4.  **资源图构建与注册**:
    -   `onResourceAdded()` 内部调用 `ResourceGraphManager.addOrUpdateResource()`。
    -   **路径解析**: `ResourcePathResolver` 将文件路径转换为 `ResourceLocation`。
    -   **元数据加载**: `ResourceMetadataLoader` 处理对应的 `.meta.json`，或生成默认元数据。
    -   **节点创建**: 在资源图中创建此脚本的 `ResourceNode`。
    -   **依赖计算**: `DependencyCalculator` 计算脚本依赖。
    -   **处理器逻辑**: `JavaScriptHandler` 读取脚本内容，创建 `OJSScript` 实例。
    -   **注册**: `OJSScript` 被注册到 `SparkRegistries.JS_SCRIPTS` 动态注册表中。

5.  **服务端首次执行**:
    -   服务器世界加载时，`SparkJsApplier.onLevelLoad` 被触发。
    -   调用 `ServerSparkJS.instance.loadAllFromRegistry()`。
    -   `ServerSparkJS` 从注册表中获取所有脚本，并在服务器主线程上逐个 `eval` 执行。
    -   执行后，每个 `JSApi` 模块的 `onLoad()` 方法被调用，完成技能、命令等的最终注册。

### 3.2 阶段二：客户端连接与全量同步

1.  **玩家登录**: 新玩家连接到服务器。
2.  **全量同步任务**:
    -   `JSScriptDataSendingTask` 被创建。
    -   它从 `SparkRegistries.JS_SCRIPTS` 收集所有 `OJSScript` 实例。
    -   所有脚本数据被打包成一个 `JSScriptDataSyncPayload`。
3.  **数据包发送**: 该数据包被发送到新连接的客户端。
4.  **客户端处理**:
    -   `JSScriptDataSyncPayload.handleInClient` 方法被调用。
    -   客户端首先清空本地的 `SparkRegistries.JS_SCRIPTS`。
    -   然后，将数据包中的所有脚本注册到本地注册表。
    -   最后，调用 `ClientSparkJS.instance.loadAllFromRegistry()`，在渲染线程上执行所有脚本，并调用 `JSApi.onLoad()`。

### 3.3 阶段三：热重载与增量同步

1.  **文件变更**: 开发者在文件系统中修改、创建或删除了一个 `.js` 文件。
2.  **事件捕获**: `ResHotReloadService` 捕获到文件变更事件。
3.  **处理器响应**:
    -   根据文件路径，确定应由 `JavaScriptHandler` 处理。
    -   调用 `onResourceAdded`, `onResourceModified`, 或 `onResourceRemoved`。
4.  **资源图与注册表更新**:
    -   流程与**阶段一**的第4步类似，`ResourceGraphManager` 更新图节点，`JavaScriptHandler` 更新 `SparkRegistries.JS_SCRIPTS` 中的 `OJSScript` 实例。
5.  **服务端重新加载**:
    -   `DynamicAwareRegistry` 的 `onDynamicRegister` (或 `onDynamicUnregister`) 回调被触发。
    -   该回调调用 `ServerSparkJS.instance.reloadScript()` 或 `unloadScript()`。
    -   `ServerSparkJS` 会调用相应 `JSApi` 的 `onReload()` 方法清理旧状态，然后在服务器主线程重新执行脚本。
6.  **增量同步**:
    -   `onDynamicRegister` 回调同时会创建一个 `JSIncrementalSyncS2CPacket`。
    -   该包包含变更的脚本内容和操作类型 (`ADDED`, `MODIFIED`, `REMOVED`)。
    -   数据包被广播给所有在线客户端。
7.  **客户端应用变更**:
    -   客户端接收到增量同步包。
    -   根据操作类型，更新本地注册表中的单个 `OJSScript`。
    -   调用 `ClientSparkJS.instance.reloadScript()` 或 `unloadScript()`，在渲染线程上应用脚本变更。

## 4. 关键问题与解决方案

### 4.1 问题：线程安全
- **描述**: 文件系统事件在I/O线程触发，而Rhino JS引擎不是线程安全的。
- **解决方案**: `ServerSparkJS` 和 `ClientSparkJS` 的 `executeScript` 方法内部会使用 `server.execute` 和 `minecraft.execute` 将脚本的执行切换到相应的游戏主线程或渲染线程，确保线程安全。

### 4.2 问题：数据一致性
- **描述**: 如何保证服务端和客户端的脚本及其状态一致？
- **解决方案**:
    - **单一数据源**: 服务端 `SparkRegistries.JS_SCRIPTS` 是所有脚本的权威来源。
    - **双重同步**: 玩家登录时的全量同步确保了初始状态一致，运行时的增量同步保证了后续变更的实时一致。

### 4.3 问题：脚本执行时机
- **描述**: 脚本需要在其依赖的API（如`SkillManager`）准备好之后执行。
- **解决方案**:
    - `JSApi` 模块的 `onLoad()` 方法提供了一个明确的回调时机。脚本在 `eval` 阶段通常只做声明（如 `Skill.create`），实际的注册和初始化逻辑则在 `onLoad` 中执行，此时所有脚本都已加载，核心API也已可用。

## 5. 总结

SparkCore的JavaScript运行时流程是一个健壮、事件驱动的系统。它利用了新的资源图架构进行资源管理和依赖追踪，通过双重同步机制保证了数据一致性，并解决了线程安全问题，为开发者提供了一个可靠且高效的脚本热重载开发体验。 