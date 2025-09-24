package cn.solarmoon.spark_core.animation.anim.play.layer

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.sync.AnimPlayPayload
import cn.solarmoon.spark_core.animation.sync.AnimStopPayload
import cn.solarmoon.spark_core.molang.core.storage.IForeignVariableStorage
import cn.solarmoon.spark_core.molang.core.storage.IScopedVariableStorage
import cn.solarmoon.spark_core.molang.core.storage.ITempVariableStorage
import cn.solarmoon.spark_core.molang.core.storage.VariableStorage
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.neoforged.neoforge.network.PacketDistributor

class AnimController(
    val animatable: IAnimatable<*>
) {

    val originAnimations get() = OAnimationSet.getOrEmpty(animatable.modelController.model?.index?.location)

    val animLayers = mutableMapOf<ResourceLocation, AnimationLayer>()

    val blendSpace = BlendSpace(animLayers)

    var speedChangeTime: Int = 0
        private set
    var overallSpeed: Double = 1.0
        private set

    /**
     * 临时变量存储，用于存储动画结束后自动销毁的临时变量，格式:t.name或temp.name
     */
    val tempStorage: ITempVariableStorage = VariableStorage()

    /**
     * 作用域变量存储，用于存储动画过程中的变量，格式:v.name或variable.name
     */
    val scopedStorage: IScopedVariableStorage = VariableStorage()

    /**
     * 外置变量存储，用于存储外部传入的变量，格式:c.name……吗？尚不明确
     */
    val foreignStorage: IForeignVariableStorage = VariableStorage()

    init {
        addLayer(AnimationLayer(DefaultLayer.BASE_LAYER, 0))
        addLayer(AnimationLayer(DefaultLayer.BASE_ADDITIVE_LAYER, 1))
        addLayer(AnimationLayer(DefaultLayer.MAIN_LAYER, 2))
        addLayer(AnimationLayer(DefaultLayer.MAIN_ADDITIVE_LAYER, 3))
        addLayer(AnimationLayer(DefaultLayer.TEMPORARY_LAYER, 4))
    }

    val isPlayingAnim get() = animLayers.values.any { it.isPlaying }

    fun addLayer(layer: AnimationLayer) {
        animLayers[layer.id] = layer
    }

    fun getLayer(id: ResourceLocation) = animLayers[id] ?: throw IllegalArgumentException("动画层 $id 不存在")

    fun stopAllAnimation(transitionTime: Int = 0) {
        animLayers.values.forEach { it.stopAnimation(transitionTime) }
    }

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

    fun stopAnimToClient(layerId: ResourceLocation, transitionTime: Int, exceptPlayer: ServerPlayer? = null) {
        exceptPlayer?.let {
            PacketDistributor.sendToPlayersNear(it.serverLevel(), exceptPlayer, exceptPlayer.x, exceptPlayer.y, exceptPlayer.z, 512.0, AnimStopPayload(animatable, layerId, transitionTime))
        } ?: run {
            PacketDistributor.sendToAllPlayers(AnimStopPayload(animatable, layerId, transitionTime))
        }
    }

    fun stopAnimToServer(layerId: ResourceLocation, transitionTime: Int) {
        val player = animatable.animatable
        if (player !is Player || !player.isLocalPlayer) throw IllegalArgumentException("只有本地玩家才能暂停动画到服务端，对于一般实体请以服务端为准同步到客户端")
        PacketDistributor.sendToServer(AnimStopPayload(animatable, layerId, transitionTime))
    }

    fun physTick() {
        animLayers.forEach { (_, layer) -> layer.physicsTick(overallSpeed) }

        animatable.modelController.model?.let { model ->
            model.origin.bones.forEach { (boneName, bone) ->
                val bonePose = model.pose.getBonePoseOrCreateEmpty(boneName)
                bonePose.updateInternal(blendSpace.blendBone(boneName, animatable))
            }
        }

        if (speedChangeTime > 0) speedChangeTime--
        else overallSpeed = 1.0
    }

    fun tick() {
        animatable.modelController.model?.let { model ->
            model.origin.bones.forEach {
                model.pose.getBonePoseOrCreateEmpty(it.key).setChanged()
            }
        }

        animLayers.forEach { (_, layer) -> layer.tick() }
    }

}