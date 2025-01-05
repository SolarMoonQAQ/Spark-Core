package cn.solarmoon.spark_core.animation.anim

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.part.Animation
import cn.solarmoon.spark_core.animation.anim.part.BoneAnim
import cn.solarmoon.spark_core.animation.anim.part.InterpolationType
import cn.solarmoon.spark_core.animation.anim.part.KeyFrame
import cn.solarmoon.spark_core.animation.anim.part.Loop
import cn.solarmoon.spark_core.data.SimpleJsonListener
import cn.solarmoon.spark_core.phys.toRadians
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.GsonHelper
import net.minecraft.util.profiling.ProfilerFiller
import org.joml.Vector3f
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.div
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
            val originSet = AnimationSet.CODEC.decode(JsonOps.INSTANCE, json).orThrow.first

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

            AnimationSet.ORIGINS.compute(root) { key, value -> value?.apply { this.animations.putAll(originSet.animations) } ?: originSet }
        }
        SparkCore.LOGGER.info("已加载 ${AnimationSet.ORIGINS.size} 种类型的动画文件，并在其中加载了 ${AnimationSet.get(ResourceLocation.withDefaultNamespace("player")).animations.size} 个玩家动画")
    }

}