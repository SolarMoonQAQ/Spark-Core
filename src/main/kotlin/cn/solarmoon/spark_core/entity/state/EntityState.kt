package cn.solarmoon.spark_core.entity.state

import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Pose

enum class EntityState(private val condition: (Entity) -> Boolean) {
    JUMP( { it.isJumping() } ),
    SPRINTING( { it.isSprinting && it.getServerMoveSpeed() > 0.145 } ),
    WALK_BACK( { it.isMovingBack() || (it.level().isClientSide && it is LocalPlayer && it.moveBackCheck()) } ),
    WALK( { it.isMoving() || (it.level().isClientSide && it is LocalPlayer && it.moveCheck()) } ),
    IDLE( { it.pose == Pose.STANDING } );

    fun getName() = toString().lowercase()

    fun getCondition(entity: Entity): Boolean {
        return condition.invoke(entity)
    }

    companion object {
        @JvmStatic
        fun get(entity: Entity): EntityState {
            for (state in EntityState.entries) {
                if (state.getCondition(entity)) return state
            }
            return IDLE
        }
    }

}