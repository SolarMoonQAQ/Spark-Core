package cn.solarmoon.spark_core.animation.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import net.minecraft.world.level.Level
import java.util.concurrent.ConcurrentLinkedQueue

object AnimationSyncState {
    @Volatile
    private var typedAnimationsReady: Boolean = false

    // 在客户端索引准备就绪之前为传入的TypedAnimPlay有效负载排队
    private val pendingTypedAnimPlays: ConcurrentLinkedQueue<TypedAnimPlayPayload> = ConcurrentLinkedQueue()

    fun markTypedAnimationsReady() {
        typedAnimationsReady = true
    }

    fun reset() {
        typedAnimationsReady = false
        pendingTypedAnimPlays.clear()
    }

    fun isTypedAnimationsReady(): Boolean = typedAnimationsReady

    fun enqueueTypedAnimPlay(payload: TypedAnimPlayPayload) {
        pendingTypedAnimPlays.add(payload)
    }

    fun flushTypedAnimPlays(level: Level) {
        var applied = 0
        while (true) {
            val payload = pendingTypedAnimPlays.poll() ?: break
            try {
                val entity = payload.syncerType.getSyncer(level, payload.syncData)
                val anim = SparkRegistries.TYPED_ANIMATION.byId(payload.animId)
                if (entity is IAnimatable<*> && anim != null) {
                    anim.play(entity, payload.layerId, payload.layerData)
                    applied++
                }
            } catch (e: Exception) {
                SparkCore.LOGGER.error("无法应用排队的TypedAnimPlay有效负载", e)
            }
        }
        if (applied > 0) {
            SparkCore.LOGGER.info("应用了 {} 个排队的类型化动画在同步就绪后播放。", applied)
        }
    }
}
