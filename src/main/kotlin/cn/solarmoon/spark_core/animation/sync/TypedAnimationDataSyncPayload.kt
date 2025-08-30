package cn.solarmoon.spark_core.animation.sync

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimation
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.registry.dynamic.DynamicIdManager
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.LinkedHashMap

/**
 * 用于全量同步TypedAnimation数据的Payload
 * @param animations TypedAnimation数据 Map<ResourceLocation, TypedAnimationData>
 */
data class TypedAnimationDataSyncPayload(
    val animations: LinkedHashMap<ResourceLocation, TypedAnimationData>
) : CustomPacketPayload {

    /**
     * TypedAnimation的序列化数据
     * @param animIndex 动画索引
     * @param assignedId 服务端分配的ID
     */
    data class TypedAnimationData(
        val animIndex: AnimIndex,
        val assignedId: Int
    ) {
        companion object {
            @JvmField
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, TypedAnimationData> = StreamCodec.composite(
                StreamCodec.of<FriendlyByteBuf, AnimIndex>(
                    { buf, animIndex ->
                        buf.writeResourceLocation(animIndex.index)
                        buf.writeUtf(animIndex.name)
                        buf.writeBoolean(animIndex.useShortcutConversion)
                    },
                    { buf ->
                        val index = buf.readResourceLocation()
                        val name = buf.readUtf()
                        val useShortcut = buf.readBoolean()
                        AnimIndex(index, name, useShortcut)
                    }
                ),
                TypedAnimationData::animIndex,
                ByteBufCodecs.INT,
                TypedAnimationData::assignedId,
                ::TypedAnimationData
            )
        }
    }

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<TypedAnimationDataSyncPayload> = 
            CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "typed_animation_data_full_sync"))

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, TypedAnimationDataSyncPayload> = StreamCodec.composite(
            ByteBufCodecs.map(
                { LinkedHashMap<ResourceLocation, TypedAnimationData>() },
                ResourceLocation.STREAM_CODEC,
                TypedAnimationData.STREAM_CODEC
            ),
            TypedAnimationDataSyncPayload::animations,
            ::TypedAnimationDataSyncPayload
        )

        /**
         * 在客户端处理此Payload的逻辑
         */
        @JvmStatic
        fun handleInClient(payload: TypedAnimationDataSyncPayload, context: IPayloadContext) {
            context.enqueueWork {
                SparkCore.LOGGER.info("客户端接收到全量TypedAnimation同步数据. 动画数量: {}", payload.animations.size)
                
                try {
                    val dynamicRegistry = SparkRegistries.TYPED_ANIMATION
                    
                    // 清空客户端动态注册表
                    dynamicRegistry.clearDynamic()
                    SparkCore.LOGGER.info("已清空客户端 TYPED_ANIMATION 动态注册表")
                    
                    // 将全量同步数据注册到客户端动态注册表
                    payload.animations.forEach { (location, animData) ->
                        // 先同步ID映射
                        val registryName = dynamicRegistry.key().location().toString()
                        DynamicIdManager.applySyncedId(registryName, location, animData.assignedId)
                        
                        // 创建TypedAnimation实例
                        val animation = TypedAnimation(animData.animIndex) {}
                        
                        // 注册到动态注册表（不触发回调，避免客户端发包给服务端）
                        val moduleId = location.namespace // 从ResourceLocation提取模块ID
                        dynamicRegistry.registerDynamic(location, animation, moduleId, replace = true, triggerCallback = false)
                        
                        SparkCore.LOGGER.debug("全量同步：已注册TypedAnimation到动态注册表: {} (ID: {})", location, animData.assignedId)
                    }

                    SparkCore.LOGGER.info("全量TypedAnimation同步完成，总计注册 {} 个动画", payload.animations.size)
                    
                    // 标记就绪并向服务器发送确认包
                    AnimationSyncState.markTypedAnimationsReady()
                    // Defer flushing queued plays until a safe context (no player access here)
                    val ack = TypedAnimationDataSendingTask.AckPayload(payload.animations.size)
                    context.reply(ack)
                    SparkCore.LOGGER.info("已向服务器发送TypedAnimation全量同步确认，并标记客户端动画索引就绪")
                } catch (e: Exception) {
                    SparkCore.LOGGER.error("处理TypedAnimation全量同步数据时发生错误", e)
                }
            }.exceptionally { e -> 
                SparkCore.LOGGER.error("客户端处理TypedAnimation全量同步时发生异常", e)
                null
            }
        }
    }

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
}
