package cn.solarmoon.spark_core.skill_tree

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent

object SkillTreeApplier {

    @SubscribeEvent
    private fun tick(event: EntityTickEvent.Pre) {
        val entity = event.entity
        if (entity !is LivingEntity) return

//        if (entity.currentSkillSet != entity.getSkillTrees()) {
//            entity.onSkillSetChanged(entity.currentSkillSet, entity.getSkillTrees())
//            entity.currentSkillSet = entity.getSkillTrees()
//        }
    }

    private fun Entity.onSkillSetChanged(last: SkillTreeSet?, new: SkillTreeSet?) {
        if (this is Player && this.isLocalPlayer) {
//            AnimStateMachineManager.getStateMachine(this)?.processEventBlocking(PlayerStateAnimMachine.ResetEvent)
            last?.forEach {
                it.currentSkill?.endOnClient()
                it.reset()
            }
        }
    }

}