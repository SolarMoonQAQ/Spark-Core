package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.js.JSComponent
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.graalvm.polyglot.HostAccess

object JSDamageSourceHelper: JSComponent() {

    @HostAccess.Export
    fun create(level: Level, id: String, directEntity: Entity?, causeEntity: Entity?, sourcePosition: Vec3?): DamageSource {
        val type = level.registryAccess().registry(Registries.DAMAGE_TYPE).get().getHolder(ResourceLocation.parse(id)).get()
        return DamageSource(type, directEntity, causeEntity, sourcePosition)
    }

}