package cn.solarmoon.spark_core.util

import net.minecraft.server.MinecraftServer
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.util.concurrent.CompletableFuture


object ServerThreading {
    @JvmStatic
    fun serverOrNull(): MinecraftServer? = ServerLifecycleHooks.getCurrentServer()


    @JvmStatic
    fun isServerThread(): Boolean {
        val server = serverOrNull() ?: return false
        return server.isSameThread
    }

    @JvmStatic
    fun runOnServer(block: (MinecraftServer) -> Unit): Boolean {
        val server = serverOrNull() ?: return false
        if (server.isSameThread) block(server) else server.execute { block(server) }
        return true
    }

    @JvmStatic
    fun <T> callOnServerBlocking(block: (MinecraftServer) -> T): T? {
        val server = serverOrNull() ?: return null
        if (server.isSameThread) return block(server)
        val f = CompletableFuture<T>()
        server.execute {
            try {
                f.complete(block(server))
            } catch (t: Throwable) {
                f.completeExceptionally(t)
            }
        }
        return f.get()
    }
}

