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
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.GsonHelper
import net.minecraft.util.profiling.ProfilerFiller
import org.joml.Vector3f

class EntityAnimListener: SimpleJsonListener("geo/animation") {
    override fun apply(
        reads: Map<ResourceLocation, JsonElement>,
        resourceManager: ResourceManager,
        profiler: ProfilerFiller
    ) {
        reads.forEach { id, json ->
            val root = ResourceLocation.parse(id.toString().substringBefore("/"))
            val animations = json.asJsonObject.getAsJsonObject("animations")
            val animationList = arrayListOf<Animation>()
            animations.entrySet().forEach { anim ->
                val animName = anim.key
                val animJson = anim.value.asJsonObject
                val loop = Loop.valueOf(getLoopAsString(animJson))
                val baseLifeTime = GsonHelper.getAsDouble(animJson, "animation_length")
                val bones = GsonHelper.getAsJsonObject(animJson, "bones")
                val boneList = ArrayList<BoneAnim>()
                bones.entrySet().forEach { bone ->
                    val boneName = bone.key
                    val transform = bone.value.asJsonObject
                    val rotMap = arrayListOf<KeyFrame>()
                    val posMap = arrayListOf<KeyFrame>()
                    val scaleMap = arrayListOf<KeyFrame>()
                    tryPut(transform, rotMap, "rotation")
                    tryPut(transform, posMap, "position")
                    tryPut(transform, scaleMap, "scale")
                    // 修正
                    rotMap.sortBy { it.timestamp }
                    posMap.sortBy { it.timestamp }
                    scaleMap.sortBy { it.timestamp }
                    rotMap.forEach { it.targetPre.apply { x = -x.toRadians(); y = -y.toRadians(); z = z.toRadians() }; it.targetPost.apply { x = -x.toRadians(); y = -y.toRadians(); z = z.toRadians() }; }
                    posMap.forEach { it.targetPre.apply { x = -x; }.div(16f); it.targetPost.apply { x = -x; }.div(16f); }
                    boneList.add(BoneAnim(boneName, baseLifeTime, rotMap, posMap, scaleMap))
                }
                animationList.add(Animation(animName, loop, baseLifeTime, boneList))
            }
            AnimationSet.ORIGINS.compute(root) { key, value -> value?.apply { this.animations.addAll(animationList) } ?: AnimationSet(animationList) }
        }
        SparkCore.LOGGER.info("已加载 ${AnimationSet.ORIGINS.size} 种类型的动画文件，并在其中加载了 ${AnimationSet.get(ResourceLocation.withDefaultNamespace("player")).animations.size} 个玩家动画")
    }

    fun getLoopAsString(animJson: JsonObject): String {
        return when {
            animJson.has("loop") -> {
                val element = animJson["loop"]
                when {
                    element.isJsonPrimitive && element.asJsonPrimitive.isString -> element.asString.uppercase()
                    element.isJsonPrimitive && element.asJsonPrimitive.isBoolean -> {
                        if (element.asBoolean) "TRUE" else "ONCE"
                    }
                    else -> "ONCE" // 默认值
                }
            }
            else -> "ONCE" // 默认值
        }
    }

    private fun tryPut(transform: JsonObject, mapToPut: ArrayList<KeyFrame>, name: String) {
        try {
            // 适用于带时间戳的情况
            // 基岩版：
            // "scale": {
            //      "0.0": [0, 0, 0],
            //      "0.0": {
            //          "pre": [0, 0, 0],
            //          "post": [0, -367.5, 0],
            //          "lerp_mode": "catmullrom"
            //      }
            // }
            // Geckolib:
            // "scale": {
            //      "0.0": {
            //          "vector": [-25, 0, 0],
            //          "easing": "linear"
            //      }
            // }
            val rotations = GsonHelper.getAsJsonObject(transform, name, null)
            putVector3fToMap(rotations, mapToPut)
        } catch (e: Exception) {
            try {
                // 适用于 "rotation": [0, 0, 0] 或 "rotation": { "vector": [0, 0, 0] } 这种直接跟数组的情况
                val single = try {
                    GsonHelper.getAsJsonArray(transform, name)
                } catch (e: Exception) {
                    GsonHelper.getAsJsonArray(GsonHelper.getAsJsonObject(transform, name), "vector")
                }
                val sg = Vector3f(single[0].asFloat, single[1].asFloat, single[2].asFloat)
                mapToPut.add(KeyFrame(0f, sg, sg, InterpolationType.LINEAR))
            } catch (e: Exception) {
                // 适用于 "scale": 0 这种直接跟数字的情况
                val single = try {
                    GsonHelper.getAsFloat(transform, name)
                } catch (e: Exception) {
                    throw IllegalAccessException(GsonHelper.getAsJsonObject(transform, name).toString() + "/" + e.message.toString())
                }
                val sg = Vector3f(single)
                mapToPut.add(KeyFrame(0f, sg, sg, InterpolationType.LINEAR))
            }
        }
    }

    private fun putVector3fToMap(jsonObject: JsonObject?, mapToPut: ArrayList<KeyFrame>) {
        jsonObject?.entrySet()?.forEach { v ->
            val timestamp = v.key.toFloatOrNull()
            var trans = "linear"
            var pre: JsonArray? = null
            var post: JsonArray? = null
            try {
                // geckolib写法
                val k = v.value.asJsonObject
                if (k.has("lerp_mode")) trans = GsonHelper.getAsString(k, "lerp_mode")
                if (k.has("post")) post = GsonHelper.getAsJsonArray(GsonHelper.getAsJsonObject(k, "post"), "vector")
                if (k.has("pre")) pre = GsonHelper.getAsJsonArray(GsonHelper.getAsJsonObject(k, "pre"), "vector")
                if (k.has("vector")) {
                    post = GsonHelper.getAsJsonArray(k, "vector")
                    pre = GsonHelper.getAsJsonArray(k, "vector")
                }
            } catch (e: Exception) {
                // 基岩版
                try {
                    var k = v.value.asJsonObject
                    if (k.has("lerp_mode")) trans = GsonHelper.getAsString(k, "lerp_mode")
                    if (k.has("post")) post = GsonHelper.getAsJsonArray(k, "post")
                    if (k.has("pre")) pre = GsonHelper.getAsJsonArray(k, "pre")
                } catch (e: Exception) {
                    pre = v.value.asJsonArray
                    post = v.value.asJsonArray
                }
            }

            if (pre == null && post != null) { pre = post } else if (pre != null && post == null) { post = pre }

            val scalePre = Vector3f(pre!![0].asFloat, pre[1].asFloat, pre[2].asFloat)
            val scalePost = Vector3f(post!![0].asFloat, post[1].asFloat, post[2].asFloat)
            val transitionType = when(trans) {
                "linear" -> InterpolationType.LINEAR
                "catmullrom" -> InterpolationType.CATMULLROM
                else -> InterpolationType.LINEAR
            }
            mapToPut.add(KeyFrame(timestamp ?: 0.0f, scalePre, scalePost, transitionType))
        }
    }

}