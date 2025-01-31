package cn.solarmoon.spark_core.animation.anim.play

import cn.solarmoon.spark_core.physics.rotLerp
import net.minecraft.world.phys.Vec3

data class KeyAnimData(
    val position: Vec3 = Vec3.ZERO,
    val rotation: Vec3 = Vec3.ZERO,
    val scale: Vec3 = Vec3(1.0, 1.0, 1.0)
) {

    fun lerp(target: KeyAnimData, progress: Double): KeyAnimData {
        return KeyAnimData(
            position.lerp(target.position, progress),
            rotation.rotLerp(target.rotation, progress),
            scale.lerp(target.scale, progress)
        )
    }

}