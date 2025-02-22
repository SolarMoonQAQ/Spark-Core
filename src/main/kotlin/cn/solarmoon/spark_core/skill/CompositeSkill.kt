package cn.solarmoon.spark_core.skill

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level
import net.neoforged.bus.api.Event
import net.neoforged.neoforge.network.handling.IPayloadContext

abstract class CompositeSkill(
    var children: List<Skill>
): Skill() {

    override fun onUpdate() {
        children.forEach {
            it.update()
        }
    }

    override fun onEnd() {
        children.forEach {
            it.end()
        }
    }

    override fun handleEvent(event: Event) {
        children.forEach {
            if (it.isActive) it.handleEvent(event)
        }
    }

    override fun sync(data: CompoundTag, context: IPayloadContext) {
        children.forEach {
            if (it.isActive) it.sync(data, context)
        }
    }

    override fun new(id: Int, type: SkillType<*>, host: SkillHost, level: Level): Skill {
        val composite = super.new(id, type, host, level) as CompositeSkill
        composite.children = composite.children.mapIndexed { index, skill ->
            skill.new(id, type, host, level)
        }
        return composite
    }

}