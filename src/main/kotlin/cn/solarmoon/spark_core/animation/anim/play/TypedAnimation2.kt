package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.anim.play.layer.AnimLayerData
import cn.solarmoon.spark_core.animation.sync.TypedAnim2PlayPayload
import cn.solarmoon.spark_core.sync.Syncer
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import java.util.concurrent.atomic.AtomicInteger

class TypedAnimation2(
    val index: AnimIndex,
    private val provider: TypedAnimProvider
) {

    companion object {
        val ID_COUNTER = AtomicInteger()
        val ALL = mutableMapOf<Int, TypedAnimation2>()

        fun get(id: Int) = ALL[id]
    }

    val id = ID_COUNTER.incrementAndGet()

    init {
        ALL[id] = this
    }

    fun exist(animatable: IAnimatable<*>? = null) =
        if (animatable == null) OAnimationSet.getOrEmpty(index.modelIndex.location).getAnimation(index.name) != null
        else animatable.animController.originAnimations.hasAnimation(index.name)

    fun create(animatable: IAnimatable<*>): AnimInstance {
        return AnimInstance.create(animatable, index) {
            provider.invoke(this)
        }!!
    }

    fun play(animatable: IAnimatable<*>, layerId: ResourceLocation, data: AnimLayerData) {
        animatable.animController.getLayer(layerId).setAnimation(create(animatable), data)
    }

    fun playToClient(syncer: Syncer, layerId: ResourceLocation, data: AnimLayerData, exceptPlayer: ServerPlayer? = null) {
        exceptPlayer?.let {
            PacketDistributor.sendToPlayersNear(it.serverLevel(), exceptPlayer, exceptPlayer.x, exceptPlayer.y, exceptPlayer.z, 512.0, TypedAnim2PlayPayload(syncer, id, layerId, data))
        } ?: run {
            PacketDistributor.sendToAllPlayers(TypedAnim2PlayPayload(syncer, id, layerId, data))
        }
    }

    fun playToServer(syncer: Syncer, layerId: ResourceLocation, data: AnimLayerData) {
        PacketDistributor.sendToServer(TypedAnim2PlayPayload(syncer, this.id, layerId, data))
    }

    override fun equals(other: Any?): Boolean {
        return (other as? TypedAnimation2)?.id == this.id
    }

    override fun hashCode(): Int {
        return id
    }

}