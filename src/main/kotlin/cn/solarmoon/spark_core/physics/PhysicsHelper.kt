package cn.solarmoon.spark_core.physics

import cn.solarmoon.spark_core.physics.collision.PhysicsObjectEvent
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.math.Matrix3f
import com.jme3.math.Quaternion
import com.jme3.math.Transform
import com.jme3.math.Vector3f
import com.jme3.system.JmeSystem
import com.jme3.system.Platform.Os.*
import net.minecraft.world.phys.Vec3
import net.neoforged.fml.ModLoadingException
import net.neoforged.fml.ModLoadingIssue
import org.joml.Matrix4f

fun selectLib(): String {
    val platform = JmeSystem.getPlatform()
    val libName = when (platform.os) {
        Windows -> "bulletjme.dll"
        Linux -> "libbulletjme.so"
        MacOS -> "libbulletjme.dylib"
        Android -> "libbulletjme.so"
        else -> throw ModLoadingException(ModLoadingIssue.error("Bullet 物理库不支持该系统平台: ${platform.os}"))
    }
    return libName
}

fun Vec3.toBVector3f() = Vector3f(x.toFloat(), y.toFloat(), z.toFloat())

fun org.joml.Vector3f.toBVector3f() = Vector3f(x, y ,z)

fun org.joml.Matrix3f.toBMatrix3f() = Matrix3f(
    m00, m01, m02,
    m10, m11, m12,
    m20, m21, m22
)

inline fun <reified T> PhysicsCollisionObject.getOwner() = owner as? T

inline fun <reified T: PhysicsObjectEvent> PhysicsCollisionObject.onEvent(crossinline handler: PhysicsCollisionObject.(T) -> Unit) {
    addEventListener(T::class.java) { handler(this, it) }
}