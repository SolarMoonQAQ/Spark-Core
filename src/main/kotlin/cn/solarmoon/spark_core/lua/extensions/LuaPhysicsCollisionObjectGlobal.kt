package cn.solarmoon.spark_core.lua.extensions

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.js.JSComponent
import cn.solarmoon.spark_core.js.call
import cn.solarmoon.spark_core.js.toVec3
import cn.solarmoon.spark_core.lua.doc.LuaGlobal
import cn.solarmoon.spark_core.lua.execute
import cn.solarmoon.spark_core.physics.presets.ticker.MoveWithAnimatedBoneTicker
import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.util.div
import cn.solarmoon.spark_core.util.toVec3
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import li.cil.repack.com.naef.jnlua.LuaValueProxy
import net.minecraft.world.phys.Vec3
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeArray
import java.util.UUID

@LuaGlobal("PhysicsCollisionObject")
object LuaPhysicsCollisionObjectGlobal: JSComponent() {

    fun createCollisionBoxBoundToBone(animatable: IAnimatable<*>, boneName: String, size: DoubleArray, offset: DoubleArray) = createCollisionBoxBoundToBone(animatable, boneName, size, offset, null)

    fun createCollisionBoxBoundToBone(animatable: IAnimatable<*>, boneName: String, size: DoubleArray, offset: DoubleArray, init: LuaValueProxy?): PhysicsCollisionObject {
        val animatable = animatable as? IEntityAnimatable<*> ?: throw IllegalArgumentException("动画体必须是实体类型！")
        val entity = animatable.animatable
        return entity.bindBody(
            PhysicsRigidBody(UUID.randomUUID().toString(), entity, BoxCollisionShape(size.toVec3().div(2.0).toBVector3f()))
        ) {
            isContactResponse = false
            isKinematic = true
            collideWithGroups = PhysicsCollisionObject.COLLISION_GROUP_NONE
            setGravity(Vector3f())
            addPhysicsTicker(MoveWithAnimatedBoneTicker(boneName, offset.toVec3().toBVector3f()))
            init?.execute(this)
        }
    }

}