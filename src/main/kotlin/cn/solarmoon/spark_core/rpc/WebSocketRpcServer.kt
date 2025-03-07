package cn.solarmoon.spark_core.rpc

import cn.solarmoon.spark_core.SparkCore
import com.google.gson.Gson
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

class WebSocketRpcServer {
    private val gson = Gson()
    private lateinit var server: CustomWebSocketServer

    fun start(port: Int = 8080) {
        server = CustomWebSocketServer(InetSocketAddress(port))
        server.start()
        SparkCore.LOGGER.info("WebSocket server started on port $port")
    }

    private inner class CustomWebSocketServer(address: InetSocketAddress) : WebSocketServer(address) {
        private val connections = ConcurrentHashMap<WebSocket, Unit>()

        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
            connections[conn] = Unit
            SparkCore.LOGGER.info("New connection established")
        }

        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
            connections.remove(conn)
            SparkCore.LOGGER.info("Connection closed")
        }

        override fun onMessage(conn: WebSocket, message: String) {
            try {
                val rpcMessage = gson.fromJson(message, RpcMessage::class.java)
                SparkCore.LOGGER.info("Received message: $message")
                when (rpcMessage.action) {
                    "playAnimation" -> {
                        val data = rpcMessage.data
                        print(data)
                        RpcClient.playAnimation(
                            name = data["name"] as String,
                            transTime = (data["transTime"] as? Number)?.toInt() ?: 0,
                            entityId = (data["entityId"] as? Number)?.toInt()
                        )
                    }
                    "replaceStateAnimation" -> {
                        val data = rpcMessage.data
                        RpcClient.replaceStateAnimation(
                            state = data["state"] as String,
                            animation = data["animation"] as String,
                            entityId = (data["entityId"] as? Number)?.toInt()
                        )
                    }
                    "blendAnimations" -> {
                        val data = rpcMessage.data
                        RpcClient.blendAnimations(
                            anim1 = data["anim1"] as String,
                            anim2 = data["anim2"] as String,
                            weight = (data["weight"] as? Number)?.toDouble() ?: 0.5,
                            entityId = (data["entityId"] as? Number)?.toInt()
                        )
                    }
                    "loadModel" -> {
                        val data = rpcMessage.data
                        RpcClient.loadModel(data["path"] as String)
                    }
                }

                // 发送成功响应
                conn.send(gson.toJson(mapOf(
                    "status" to "success",
                    "action" to rpcMessage.action
                )))
            } catch (e: Exception) {
                // 发送错误响应
                conn.send(gson.toJson(mapOf(
                    "status" to "error",
                    "message" to e.message
                )))
                SparkCore.LOGGER.error("Failed to handle RPC message: ${e.message}")
            }
        }

        override fun onError(conn: WebSocket?, ex: Exception) {
            SparkCore.LOGGER.error("WebSocket error: ${ex.message}")
        }

        override fun onStart() {
            SparkCore.LOGGER.info("WebSocket server is ready")
        }
    }

    data class RpcMessage(
        val action: String,
        val data: Map<String, Any>
    )
} 