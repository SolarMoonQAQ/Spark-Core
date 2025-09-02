package cn.solarmoon.spark_core.animation.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * 用于在资源热重载等情况下，同步单个 OAnimationSet 到客户端的 Payload。
 * @param rootLocation OAnimationSet 的根 ResourceLocation。
 * @param animationSet 要同步的 OAnimationSet 对象。
 */
data class OAnimationSetSyncPayload(
    val rootLocation: ResourceLocation,
    val animationSet: OAnimationSet
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<OAnimationSetSyncPayload> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<OAnimationSetSyncPayload>(
            ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "o_animation_set_sync")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, OAnimationSetSyncPayload> = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, OAnimationSetSyncPayload::rootLocation,
            OAnimationSet.STREAM_CODEC, OAnimationSetSyncPayload::animationSet,
            ::OAnimationSetSyncPayload
        )

        @JvmStatic
        fun handleInClient(payload: OAnimationSetSyncPayload, context: IPayloadContext) {
            context.enqueueWork {
                SparkCore.LOGGER.info("Received OAnimationSetSyncPayload for: ${payload.rootLocation}. Updating OAnimationSet.ORIGINS.")

                // 直接操作 OAnimationSet.ORIGINS，与新的 handle 机制保持一致
                if (payload.animationSet.animations.isEmpty()) {
                    // 如果动画集为空，则从 ORIGINS 中移除
                    OAnimationSet.ORIGINS.remove(payload.rootLocation)
                    SparkCore.LOGGER.debug("Removed animation set for: {}", payload.rootLocation)

                    // 同时清理AnimIndex.ORIGINS中相关的映射
                    val pathParts = payload.rootLocation.path.split("/")
                    if (pathParts.size >= 3) {
                        val entityPath = pathParts[2]
                        // 移除所有相关的快捷映射
//                        val toRemove = AnimIndex.ORIGINS.keys.filter { shortcut ->
//                            shortcut.namespace == "minecraft" && shortcut.path.startsWith("$entityPath/")
//                        }
//                        toRemove.forEach { shortcut ->
//                            AnimIndex.ORIGINS.remove(shortcut)
//                        }
                        //SparkCore.LOGGER.debug("Removed {} AnimIndex shortcuts for entity: {}", toRemove.size, entityPath)
                    }
                } else {
                    // 否则更新或添加动画集
                    OAnimationSet.ORIGINS[payload.rootLocation] = payload.animationSet
                    SparkCore.LOGGER.debug("Updated animation set for: {} with OAnimationSet object", payload.rootLocation)

                    // 同时更新AnimIndex.ORIGINS映射
                    val pathParts = payload.rootLocation.path.split("/")
                    if (pathParts.size >= 3) {
                        val entityPath = pathParts[2]
                        payload.animationSet.animations.keys.forEach { animName ->
                            val shortcutPath = ResourceLocation.fromNamespaceAndPath(
                                "minecraft",
                                "$entityPath/$animName"
                            )
                            //AnimIndex.ORIGINS[shortcutPath] = payload.rootLocation
                        }
                        SparkCore.LOGGER.debug("Updated {} AnimIndex shortcuts for entity: {}", payload.animationSet.animations.size, entityPath)
                    }
                }
                
                // TODO: 考虑是否需要进一步的客户端刷新逻辑，例如针对使用这些动画的实体进行特定更新。
            }.exceptionally { e ->
                SparkCore.LOGGER.error("Error processing OAnimationSetSyncPayload for ${payload.rootLocation} on client", e)
                null
            }
        }
    }
}
