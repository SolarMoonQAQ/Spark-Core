package cn.solarmoon.spark_core.animation.sync

//class AnimStopPayload private constructor(
//    val syncerType: SyncerType<*>,
//    val syncData: SyncData,
//    val layerId: ResourceLocation,
//    val transTime: Int
//): CustomPacketPayload {
//    constructor(syncer: Syncer, layerId: ResourceLocation, transTime: Int): this(syncer.syncerType, syncer.syncData, layerId, transTime)
//
//    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload?> {
//        return TYPE
//    }
//
//    companion object {
//        @JvmStatic
//        fun handleBothSide(payload: AnimStopPayload, context: IPayloadContext) {
//            val level = context.player().level()
//            val entity = payload.syncerType.getSyncer(level, payload.syncData)
//            if (entity !is IAnimatable<*>) return
//            val controller = entity.animController
//            val layer = entity.animController.getLayer(payload.layerId)
//            layer.stopAnimation(payload.transTime)
//            // 从单人客户端发来的同步需要再同步给其它玩家客户端
//            if (!level.isClientSide) controller.stopAnimToClient(payload.layerId, payload.transTime, context.player() as? ServerPlayer)
//        }
//
//        @JvmStatic
//        val TYPE = CustomPacketPayload.Type<AnimStopPayload>(ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "anim_stop"))
//
//        @JvmStatic
//        val STREAM_CODEC = StreamCodec.composite(
//            SyncerType.STREAM_CODEC, AnimStopPayload::syncerType,
//            SyncData.STREAM_CODEC, AnimStopPayload::syncData,
//            ResourceLocation.STREAM_CODEC, AnimStopPayload::layerId,
//            ByteBufCodecs.INT, AnimStopPayload::transTime,
//            ::AnimStopPayload
//        )
//    }
//
//}