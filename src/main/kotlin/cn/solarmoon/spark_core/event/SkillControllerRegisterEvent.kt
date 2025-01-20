package cn.solarmoon.spark_core.event

import cn.solarmoon.spark_core.skill.controller.SkillController
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.Event
import kotlin.reflect.KClass

abstract class SkillControllerRegisterEvent<T>(
    protected val skillControllers: MutableMap<String, SkillController<out T>>
): Event() {

    class Entity(
        val entity: net.minecraft.world.entity.Entity,
        skillControllers: MutableMap<String, SkillController<out net.minecraft.world.entity.Entity>>
    ): SkillControllerRegisterEvent<net.minecraft.world.entity.Entity>(skillControllers) {

        fun <T: net.minecraft.world.entity.Entity> register(classType: KClass<T>, skillController: (T) -> SkillController<out net.minecraft.world.entity.Entity>) {
            if (classType.isInstance(entity)) {
                val controller = skillController.invoke(entity as T)
                skillControllers[controller.name] = controller
            }
        }

    }

}