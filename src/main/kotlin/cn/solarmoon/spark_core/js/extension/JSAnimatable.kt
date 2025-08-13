package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimation
import cn.solarmoon.spark_core.animation.anim.play.layer.AnimLayerData
import cn.solarmoon.spark_core.animation.anim.play.layer.getMainLayer
import cn.solarmoon.spark_core.animation.sync.AnimSpeedChangePayload
import cn.solarmoon.spark_core.js.toVec3
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import cn.solarmoon.spark_core.util.toVec3
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.neoforged.neoforge.network.PacketDistributor
import org.mozilla.javascript.NativeArray
import java.awt.Color

interface JSAnimatable {

    val js_animatable get() = this as IAnimatable<*>

    val js get() = js_animatable.animLevel.jsEngine

    fun getAnimation(): AnimInstance? {
        return js_animatable.animController.getMainLayer().animation
    }

    fun playAnimation(anim: AnimInstance, transitionTime: Int) {
        js_animatable.animController.getMainLayer().setAnimation(anim, AnimLayerData(transitionTime = transitionTime))
    }

    fun createAnimation(index: String, name: String): AnimInstance {
        return AnimInstance.create(js_animatable, AnimIndex(ResourceLocation.parse(index), name))
    }

    fun createAnimation(index: String): AnimInstance {
        // 直接从注册表中获取
       return (SparkRegistries.TYPED_ANIMATION.get(ResourceLocation.parse(index)) as TypedAnimation).create(js_animatable)
    }

    fun changeSpeed(time: Int, speed: Double) {
        if (!js_animatable.animLevel.isClientSide && time > 0) {
            js_animatable.animController.changeSpeed(time, speed)
            PacketDistributor.sendToAllPlayers(AnimSpeedChangePayload(js_animatable, time, speed))
        }
    }

    fun summonShadow(maxLifeTime: Int, color: Int) {
        val animatable = js_animatable
        if (animatable is Entity && !animatable.animLevel.isClientSide) {
            SparkVisualEffects.SHADOW.addToClient(animatable.id, maxLifeTime, Color(color))
        }
    }

    fun summonSpaceWarp(
        bone: String,
        offset: NativeArray,
        radius: Float,
        strength: Float,
        lifeTime: Int,
        hz: Float
    ) {
        val animatable = js_animatable
        if (animatable is Entity && !animatable.animLevel.isClientSide) {
            SparkVisualEffects.SPACE_WARP.addToClient(
                animatable.getWorldBonePivot(bone, offset.toVec3()).toVec3(),
                radius, strength, lifeTime, hz
            )
        }
    }

//    fun summonParticleSplash(
//        bone: String,
//        offset: NativeArray,
//        radius: Float,       // 最终外圈半径
//        strength: Float,     // 力度 → 粒子初速度
//        rings: Int,          // 冲击波层数
//        forwardBias: Float,  // 前冲比例
//        density: Float       // 周向粒子密度系数（1.0 = 基准密度）
//    ) {
//        val animatable = js_animatable
//        if (animatable !is Entity) return
//        val level = animatable.level()
//
//        // 获取两帧骨骼位置，计算法向量
//        val p0 = animatable.getWorldBonePivot(bone, offset.toVec3(), 0f).toVec3()
//        val p1 = animatable.getWorldBonePivot(bone, offset.toVec3(), 1f).toVec3()
//        val baseCenter = p1
//        var dir = p1.subtract(p0).normalize()
//        if (dir.lengthSqr() < 1e-6) {
//            val fallback = animatable.getViewVector(1f)
//            dir = if (fallback.lengthSqr() > 1e-6) fallback.normalize() else Vec3(0.0, 1.0, 0.0)
//        }
//
//        // 构建正交基
//        var u = dir.cross(Vec3(0.0, 1.0, 0.0))
//        if (u.lengthSqr() < 1e-6) u = dir.cross(Vec3(1.0, 0.0, 0.0))
//        u = u.normalize()
//        val v = dir.cross(u).normalize()
//
//        val particle = ParticleTypes.CLOUD
//        val basePoints = (24 + radius * 8f).roundToInt().coerceIn(12, 96)
//
//        val rMin = 0.12
//        val coneLength = radius.toDouble() * (0.8 + 0.6 * forwardBias.coerceIn(0f, 2f))
//
//        for (ring in 0 until rings) {
//            val t = (ring + 1).toDouble() / rings.toDouble()
//            val ringCenter = baseCenter.add(dir.scale(coneLength * t))
//            val r = rMin + (radius.toDouble() - rMin) * t
//
//            val ringPoints = (basePoints * density * (0.7 + 0.3 * t))
//                .roundToInt()
//                .coerceIn(8, 160)
//
//            val radialSpeed = strength.toDouble() * (0.8 + 0.4 * t)
//            val forwardSpeed = strength.toDouble() * forwardBias.toDouble()
//
//            for (i in 0 until ringPoints) {
//                val theta = (2.0 * Math.PI * i) / ringPoints
//                val radial = u.scale(cos(theta)).add(v.scale(sin(theta)))
//                val pos = ringCenter.add(radial.scale(r))
//                val vel = radial.scale(radialSpeed).add(dir.scale(forwardSpeed))
//
//                level.addParticle(
//                    particle,
//                    pos.x, pos.y, pos.z,
//                    vel.x, vel.y, vel.z
//                )
//            }
//        }
//    }



}