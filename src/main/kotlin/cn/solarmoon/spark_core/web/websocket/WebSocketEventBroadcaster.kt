package cn.solarmoon.spark_core.web.websocket

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.event.ResourceGraphEvent
import cn.solarmoon.spark_core.event.ModuleGraphEvent
import com.google.gson.Gson
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*

/**
 * WebSocket事件广播器
 * 
 * 提供实时事件推送功能，支持资源变更、依赖更新、打包进度、冲突检测等事件的实时通知。
 * 管理WebSocket连接的生命周期，确保消息发送的可靠性。
 */
object WebSocketEventBroadcaster {
    
    private val gson = Gson()
    private val connections = CopyOnWriteArraySet<WebSocketConnection>()
    private val connectionMetadata = ConcurrentHashMap<String, ConnectionMetadata>()
    
    // 用于清理死连接的定时器
    private val cleanupExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "WebSocket-Cleanup").apply { isDaemon = true }
    }
    
    // 协程作用域
    private val broadcastScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var isInitialized = false
    
    /**
     * 初始化WebSocket事件广播器
     */
    fun initialize() {
        if (isInitialized) {
            SparkCore.LOGGER.warn("WebSocketEventBroadcaster已经初始化")
            return
        }
        
        try {
            // 注册事件监听器
            NeoForge.EVENT_BUS.register(this)
            
            // 启动死连接清理任务
            cleanupExecutor.scheduleWithFixedDelay(
                ::cleanupDeadConnections,
                30, // 初始延迟30秒
                30, // 每30秒执行一次
                TimeUnit.SECONDS
            )
            
            isInitialized = true
            SparkCore.LOGGER.info("WebSocket事件广播器已初始化")
        } catch (e: Exception) {
            SparkCore.LOGGER.error("初始化WebSocket事件广播器失败", e)
        }
    }
    
    /**
     * 关闭WebSocket事件广播器
     */
    fun shutdown() {
        if (!isInitialized) return
        
        try {
            // 注销事件监听器
            NeoForge.EVENT_BUS.unregister(this)
            
            // 关闭所有连接
            connections.forEach { connection ->
                try {
                    connection.close()
                } catch (e: Exception) {
                    SparkCore.LOGGER.debug("关闭WebSocket连接时出错", e)
                }
            }
            connections.clear()
            connectionMetadata.clear()
            
            // 关闭清理任务
            cleanupExecutor.shutdown()
            
            // 取消协程作用域
            broadcastScope.cancel()
            
            isInitialized = false
            SparkCore.LOGGER.info("WebSocket事件广播器已关闭")
        } catch (e: Exception) {
            SparkCore.LOGGER.error("关闭WebSocket事件广播器失败", e)
        }
    }
    
    /**
     * 注册WebSocket连接
     * 
     * @param connection WebSocket连接
     * @param clientInfo 客户端信息
     */
    fun registerConnection(connection: WebSocketConnection, clientInfo: String = "unknown") {
        try {
            connections.add(connection)
            connectionMetadata[connection.id] = ConnectionMetadata(
                clientInfo = clientInfo,
                connectedTime = System.currentTimeMillis(),
                lastPingTime = System.currentTimeMillis()
            )
            
            SparkCore.LOGGER.info("WebSocket连接已注册: ${connection.id} (客户端: $clientInfo)")
            
            // 发送欢迎消息
            val welcomeMessage = WebSocketMessage(
                type = "WELCOME",
                data = mapOf(
                    "connectionId" to connection.id,
                    "serverTime" to System.currentTimeMillis(),
                    "message" to "连接成功，开始接收实时事件"
                )
            )
            sendToConnection(connection, welcomeMessage)
        } catch (e: Exception) {
            SparkCore.LOGGER.error("注册WebSocket连接失败", e)
        }
    }
    
    /**
     * 注销WebSocket连接
     * 
     * @param connectionId 连接ID
     */
    fun unregisterConnection(connectionId: String) {
        try {
            connections.removeIf { it.id == connectionId }
            connectionMetadata.remove(connectionId)
            SparkCore.LOGGER.info("WebSocket连接已注销: $connectionId")
        } catch (e: Exception) {
            SparkCore.LOGGER.error("注销WebSocket连接失败", e)
        }
    }
    
    /**
     * 广播资源变更事件
     */
    fun broadcastResourceChange(node: cn.solarmoon.spark_core.resource.graph.ResourceNode, changeType: String) {
        val message = WebSocketMessage(
            type = "RESOURCE_CHANGED",
            data = mapOf(
                "resourceId" to node.id.toString(),
                "resourceName" to extractResourceName(node.id.path),
                "namespace" to node.namespace,
                "modId" to node.modId,
                "moduleName" to node.moduleName,
                "changeType" to changeType,
                "timestamp" to System.currentTimeMillis()
            )
        )
        broadcast(message)
    }
    
    /**
     * 广播依赖关系变更事件
     */
    fun broadcastDependencyChange(sourceId: String, targetId: String, edgeType: String, changeType: String) {
        val message = WebSocketMessage(
            type = "DEPENDENCY_CHANGED",
            data = mapOf(
                "sourceId" to sourceId,
                "targetId" to targetId,
                "edgeType" to edgeType,
                "changeType" to changeType,
                "timestamp" to System.currentTimeMillis()
            )
        )
        broadcast(message)
    }
    
    /**
     * 广播打包进度事件
     */
    fun broadcastPackagingProgress(moduleId: String, progress: Int, status: String, message: String) {
        val broadcastMessage = WebSocketMessage(
            type = "PACKAGING_PROGRESS",
            data = mapOf(
                "moduleId" to moduleId,
                "progress" to progress,
                "status" to status,
                "message" to message,
                "timestamp" to System.currentTimeMillis()
            )
        )
        broadcast(broadcastMessage)
    }
    
    /**
     * 广播冲突检测事件
     */
    fun broadcastConflictDetected(resourceId: String, conflictType: String, details: Map<String, Any>) {
        val message = WebSocketMessage(
            type = "CONFLICT_DETECTED",
            data = mapOf(
                "resourceId" to resourceId,
                "conflictType" to conflictType,
                "details" to details,
                "timestamp" to System.currentTimeMillis()
            )
        )
        broadcast(message)
    }
    
    /**
     * 广播模块变更事件
     */
    fun broadcastModuleChange(moduleId: String, changeType: String) {
        val message = WebSocketMessage(
            type = "MODULE_CHANGED",
            data = mapOf(
                "moduleId" to moduleId,
                "changeType" to changeType,
                "timestamp" to System.currentTimeMillis()
            )
        )
        broadcast(message)
    }
    
    /**
     * 广播消息到所有连接
     */
    private fun broadcast(message: WebSocketMessage) {
        if (connections.isEmpty()) {
            return
        }
        
        broadcastScope.launch {
            val messageJson = gson.toJson(message)
            val deadConnections = mutableListOf<WebSocketConnection>()
            
            connections.forEach { connection ->
                try {
                    if (connection.isOpen()) {
                        connection.send(messageJson)
                    } else {
                        deadConnections.add(connection)
                    }
                } catch (e: Exception) {
                    SparkCore.LOGGER.debug("发送WebSocket消息失败: ${connection.id}", e)
                    deadConnections.add(connection)
                }
            }
            
            // 清理死连接
            deadConnections.forEach { deadConnection ->
                unregisterConnection(deadConnection.id)
            }
            
            if (deadConnections.isNotEmpty()) {
                SparkCore.LOGGER.debug("清理了 ${deadConnections.size} 个死连接")
            }
        }
    }
    
    /**
     * 发送消息到特定连接
     */
    private fun sendToConnection(connection: WebSocketConnection, message: WebSocketMessage) {
        broadcastScope.launch {
            try {
                if (connection.isOpen()) {
                    val messageJson = gson.toJson(message)
                    connection.send(messageJson)
                } else {
                    unregisterConnection(connection.id)
                }
            } catch (e: Exception) {
                SparkCore.LOGGER.debug("发送WebSocket消息到特定连接失败: ${connection.id}", e)
                unregisterConnection(connection.id)
            }
        }
    }
    
    /**
     * 清理死连接
     */
    private fun cleanupDeadConnections() {
        try {
            val now = System.currentTimeMillis()
            val deadConnections = mutableListOf<WebSocketConnection>()
            
            connections.forEach { connection ->
                val metadata = connectionMetadata[connection.id]
                if (metadata != null) {
                    // 检查连接是否超时（5分钟无活动）
                    if (now - metadata.lastPingTime > 300_000) {
                        deadConnections.add(connection)
                    }
                } else {
                    // 没有元数据的连接也认为是死连接
                    deadConnections.add(connection)
                }
                
                // 检查连接状态
                if (!connection.isOpen()) {
                    deadConnections.add(connection)
                }
            }
            
            deadConnections.forEach { deadConnection ->
                unregisterConnection(deadConnection.id)
            }
            
            if (deadConnections.isNotEmpty()) {
                SparkCore.LOGGER.debug("定时清理了 ${deadConnections.size} 个死连接")
            }
        } catch (e: Exception) {
            SparkCore.LOGGER.error("清理死连接时出错", e)
        }
    }
    
    /**
     * 更新连接的ping时间
     */
    fun updateConnectionPing(connectionId: String) {
        connectionMetadata[connectionId]?.let { metadata ->
            connectionMetadata[connectionId] = metadata.copy(lastPingTime = System.currentTimeMillis())
        }
    }
    
    /**
     * 获取连接统计信息
     */
    fun getConnectionStats(): ConnectionStats {
        return ConnectionStats(
            totalConnections = connections.size,
            activeConnections = connections.count { it.isOpen() },
            connectionDetails = connectionMetadata.mapValues { (_, metadata) ->
                mapOf(
                    "clientInfo" to metadata.clientInfo,
                    "connectedTime" to metadata.connectedTime,
                    "lastPingTime" to metadata.lastPingTime,
                    "uptime" to (System.currentTimeMillis() - metadata.connectedTime)
                )
            }
        )
    }
    
    // ==================== 事件监听器 ====================
    
    /**
     * 监听资源节点变更事件
     */
    @SubscribeEvent
    fun onResourceNodeChange(event: ResourceGraphEvent.NodeChange) {
        broadcastResourceChange(event.node, event.changeType.name)
    }
    
    /**
     * 监听资源依赖关系变更事件
     */
    @SubscribeEvent
    fun onResourceDependencyChange(event: ResourceGraphEvent.DependencyChange) {
        broadcastDependencyChange(
            event.source.id.toString(),
            event.target.id.toString(),
            event.edgeType.toString(),
            event.changeType.name
        )
    }
    
    /**
     * 监听资源覆盖关系变更事件
     */
    @SubscribeEvent
    fun onResourceOverrideChange(event: ResourceGraphEvent.OverrideChange) {
        val message = WebSocketMessage(
            type = "OVERRIDE_CHANGED",
            data = mapOf(
                "overriderId" to event.overrider.id.toString(),
                "overriddenId" to event.overridden.id.toString(),
                "changeType" to event.changeType.name,
                "timestamp" to System.currentTimeMillis()
            )
        )
        broadcast(message)
    }
    
    /**
     * 监听模块节点变更事件
     */
    @SubscribeEvent
    fun onModuleNodeChange(event: ModuleGraphEvent.NodeChange) {
        broadcastModuleChange(event.moduleNode.id, event.changeType.name)
    }
    
    /**
     * 从资源路径中提取资源名称
     */
    private fun extractResourceName(path: String): String {
        val parts = path.split("/")
        return if (parts.size > 2) {
            parts.drop(2).joinToString("/") // 去掉moduleName和resourceType
        } else {
            path
        }
    }
}

/**
 * WebSocket连接接口
 */
interface WebSocketConnection {
    val id: String
    fun send(message: String)
    fun close()
    fun isOpen(): Boolean
}

/**
 * WebSocket消息
 */
data class WebSocketMessage(
    val type: String,
    val data: Map<String, Any>,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 连接元数据
 */
data class ConnectionMetadata(
    val clientInfo: String,
    val connectedTime: Long,
    val lastPingTime: Long
)

/**
 * 连接统计信息
 */
data class ConnectionStats(
    val totalConnections: Int,
    val activeConnections: Int,
    val connectionDetails: Map<String, Map<String, Any>>
)
