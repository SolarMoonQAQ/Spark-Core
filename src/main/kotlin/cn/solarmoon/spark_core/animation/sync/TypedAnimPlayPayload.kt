package cn.solarmoon.spark_core.animation.sync

//import cn.solarmoon.spark_core.SparkCore
//import cn.solarmoon.spark_core.animation.IAnimatable
//import cn.solarmoon.spark_core.animation.anim.play.layer.AnimLayerData
//import cn.solarmoon.spark_core.sync.SyncData
//import cn.solarmoon.spark_core.sync.Syncer
//import cn.solarmoon.spark_core.sync.SyncerType
//import net.minecraft.network.codec.StreamCodec
//import net.minecraft.network.protocol.common.custom.CustomPacketPayload
//import net.minecraft.resources.ResourceLocation
//import net.minecraft.server.level.ServerPlayer
//import net.neoforged.neoforge.network.handling.IPayloadContext
//
//class TypedAnimPlayPayload private constructor(
//    val syncerType: SyncerType<*>,
//    val syncData: SyncData,
//    val anim: TypedAnimation,
//    val layerId: ResourceLocation,
//    val layerData: AnimLayerData
//): CustomPacketPayload {
//    constructor(syncer: Syncer, anim: TypedAnimation, layerId: ResourceLocation, layerData: AnimLayerData): this(syncer.syncerType, syncer.syncData, anim, layerId, layerData)
//
//    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
//        return TYPE
//    }
//
//    companion object {
//        @JvmStatic
//        fun handleBothSide(payload: TypedAnimPlayPayload, context: IPayloadContext) {
//            val level = context.player().level()
//            val entity = payload.syncerType.getSyncer(level, payload.syncData)
//            if (entity !is IAnimatable<*>) return
//            val anim = payload.anim
//            anim.play(entity, payload.layerId, payload.layerData)
//            // 从单人客户端发来的同步需要再同步给其它玩家客户端
//            if (!level.isClientSide) anim.playToClient(entity, payload.layerId, payload.layerData, context.player() as? ServerPlayer)
//        }
//
//        @JvmStatic
//        val TYPE = CustomPacketPayload.Type<TypedAnimPlayPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "typed_anim_play"))
//
//        @JvmStatic
//        val STREAM_CODEC = StreamCodec.composite(
//            SyncerType.STREAM_CODEC, TypedAnimPlayPayload::syncerType,
//            SyncData.STREAM_CODEC, TypedAnimPlayPayload::syncData,
//            TypedAnimation.STREAM_CODEC, TypedAnimPlayPayload::anim,
//            ResourceLocation.STREAM_CODEC, TypedAnimPlayPayload::layerId,
//            AnimLayerData.STREAM_CODEC, TypedAnimPlayPayload::layerData,
//            ::TypedAnimPlayPayload
//        )
//    }
//
//}