package cn.solarmoon.spark_core.animation.anim

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.data.SimpleJsonListener
import cn.solarmoon.spark_core.phys.toRadians
import com.google.gson.JsonElement
import com.mojang.serialization.JsonOps
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.profiling.ProfilerFiller
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.toVec3
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.toVector3d

class EntityAnimListener: SimpleJsonListener("geo/animation") {
    override fun apply(
        reads: Map<ResourceLocation, JsonElement>,
        resourceManager: ResourceManager,
        profiler: ProfilerFiller
    ) {
        reads.forEach { id, json ->
            val root = ResourceLocation.parse(id.toString().substringBefore("/"))
            val originSet = OAnimationSet.CODEC.decode(JsonOps.INSTANCE, json).orThrow.first

            originSet.animations.values.forEach { it.bones.forEach {
                it.value.rotation.values.forEach {
                    it.pre = it.pre.toVector3d().apply { x = -x.toRadians(); y = -y.toRadians(); z = z.toRadians() }.toVec3()
                    it.post = it.post.toVector3d().apply { x = -x.toRadians(); y = -y.toRadians(); z = z.toRadians() }.toVec3()
                }
                it.value.position.values.forEach {
                    it.pre = it.pre.toVector3d().apply { x = -x; }.div(16.0).toVec3()
                    it.post = it.post.toVector3d().apply { x = -x; }.div(16.0).toVec3()
                }
            } }

            OAnimationSet.ORIGINS.compute(root) { key, value -> value?.apply { this.animations.putAll(originSet.animations) } ?: originSet }
        }
        SparkCore.LOGGER.info("已加载 ${OAnimationSet.ORIGINS.size} 种类型的动画文件，并在其中加载了 ${OAnimationSet.get(ResourceLocation.withDefaultNamespace("player")).animations.size} 个玩家动画")
    }

}