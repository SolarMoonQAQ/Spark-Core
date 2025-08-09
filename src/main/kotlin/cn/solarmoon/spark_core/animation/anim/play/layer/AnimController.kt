package cn.solarmoon.spark_core.animation.anim.play.layer

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.sync.AnimPlayPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.neoforged.neoforge.network.PacketDistributor

class AnimController(
    val animatable: IAnimatable<*>
) {

    val animLayers = mutableMapOf<ResourceLocation, AnimationLayer>()

    val blendSpace = BlendSpace(animLayers)

    var speedChangeTime: Int = 0
        private set
    var overallSpeed: Double = 1.0
        private set

    init {
        addLayer(AnimationLayer(DefaultLayer.BASE_LAYER, 0))
        addLayer(AnimationLayer(DefaultLayer.BASE_ADDITIVE_LAYER_1, 1))
        addLayer(AnimationLayer(DefaultLayer.BASE_ADDITIVE_LAYER_2, 2))
        addLayer(AnimationLayer(DefaultLayer.MAIN_LAYER, 3))
        addLayer(AnimationLayer(DefaultLayer.MAIN_ADDITIVE_LAYER, 4))
        addLayer(AnimationLayer(DefaultLayer.TEMPORARY_LAYER, 5))
    }

    val isPlayingAnim get() = animLayers.values.any { it.animation != null || it.isInTransition }

    fun addLayer(layer: AnimationLayer) {
        animLayers[layer.id] = layer
    }

    fun getLayer(id: ResourceLocation) = animLayers[id] ?: throw IllegalArgumentException("动画层 $id 不存在")

    /**
     * 在指定时间内改变动画整体速度，时间结束后复原
     */
    fun changeSpeed(time: Int, speed: Double) {
        overallSpeed = speed
        speedChangeTime = time
    }

    fun playAnimToClient(index: AnimIndex, layerId: ResourceLocation, data: AnimLayerData, exceptPlayer: ServerPlayer? = null) {
        exceptPlayer?.let {
            PacketDistributor.sendToPlayersNear(it.serverLevel(), exceptPlayer, exceptPlayer.x, exceptPlayer.y, exceptPlayer.z, 512.0, AnimPlayPayload(animatable, index, layerId, data))
        } ?: run {
            PacketDistributor.sendToAllPlayers(AnimPlayPayload(animatable, index, layerId, data))
        }
    }

    fun playAnimToServer(index: AnimIndex, layerId: ResourceLocation, data: AnimLayerData) {
        val player = animatable.animatable
        if (player !is Player || !player.isLocalPlayer) throw IllegalArgumentException("只有本地玩家才能播放动画到服务端，对于一般实体请以服务端为准同步到客户端")
        PacketDistributor.sendToServer(AnimPlayPayload(animatable, index, layerId, data))
    }

    fun physTick() {
        animLayers.forEach { (_, layer) -> layer.physicsTick(overallSpeed) }

        animatable.model.bones.forEach { (boneName, bonePose) ->
            val bone = animatable.getBonePose(boneName)
            bone.updateInternal(blendSpace.blendBone(boneName, animatable))
        }

        if (speedChangeTime > 0) speedChangeTime--
        else overallSpeed = 1.0
    }

    fun tick() {
        animatable.model.bones.forEach {
            animatable.getBonePose(it.key).setChanged()
        }

        animLayers.forEach { (_, layer) -> layer.tick() }
    }

}