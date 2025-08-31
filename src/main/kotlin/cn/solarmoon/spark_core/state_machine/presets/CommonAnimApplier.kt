package cn.solarmoon.spark_core.state_machine.presets

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.sync.ModelIndexSyncPayload
import cn.solarmoon.spark_core.event.ModelIndexChangeEvent
import cn.solarmoon.spark_core.physics.host.PhysicsHost
import cn.solarmoon.spark_core.physics.presets.callback.SparkCollisionCallback
import cn.solarmoon.spark_core.physics.presets.initWithAnimatedBone
import cn.solarmoon.spark_core.physics.presets.ticker.MoveWithAnimatedBoneTicker
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Entity
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.network.PacketDistributor

object CommonAnimApplier {

    // 服务端模型更换自动绑定碰撞箱测试
    @SubscribeEvent
    private fun onModelIndexChange(event: ModelIndexChangeEvent) {
        val entityHost = event.animatable as? IEntityAnimatable<*> ?: return
        if(entityHost.animLevel is ClientLevel) return

        val entity = entityHost as? Entity ?: run {
            println("[CommonAnimApplier] Error: entityHost could not be cast to Entity in onModelIndexChange.")
            return
        }

        entityHost.model.bones.values.filterNot { it.name in listOf("rightItem", "leftItem") }.forEach { bone ->
            val body = PhysicsRigidBody(bone.name, entityHost as PhysicsHost, CompoundCollisionShape())
            entityHost.bindBody(body, entityHost.physicsLevel, true) {
                (this.collisionShape as CompoundCollisionShape).initWithAnimatedBone(bone)
                this.isContactResponse = false
                this.setGravity(Vector3f())
                this.setEnableSleep(false)
                this.isKinematic = true
                this.collideWithGroups = PhysicsCollisionObject.COLLISION_GROUP_OBJECT or PhysicsCollisionObject.COLLISION_GROUP_BLOCK
                this.addPhysicsTicker(MoveWithAnimatedBoneTicker(bone.name))
                this.addCollisionCallback(SparkCollisionCallback(
                    owner = entityHost,
                    cbName = body.name,
                    collisionBoxId = body.name
                ))
            }
        }
        PacketDistributor.sendToAllPlayers(
            ModelIndexSyncPayload(entityHost.syncerType, entityHost.syncData, event.newModelIndex)
        )
    }
}