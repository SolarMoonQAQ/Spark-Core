package cn.solarmoon.spark_core.entry_builder.common

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageType
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3


class DamageTypeBuilder(private val modId: String) {//

    private var id = ""

    fun id(id: String) = apply { this.id = id }

    fun build(): DamageTypeEntry {
        return DamageTypeEntry(ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(modId, id)))
    }

    class DamageTypeEntry(val damageTypeResourceKey: ResourceKey<DamageType>) {

        fun get(level: Level): DamageSource {
            return DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(damageTypeResourceKey)
            )
        }

        fun get(level: Level, directEntity: Entity): DamageSource {
            return DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(damageTypeResourceKey),
                directEntity
            )
        }

        fun get(level: Level, directEntity: Entity, causingEntity: Entity): DamageSource {
            return DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(damageTypeResourceKey),
                directEntity, causingEntity
            )
        }

        fun get(level: Level, directEntity: Entity, causingEntity: Entity, position: Vec3): DamageSource {
            return DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(damageTypeResourceKey),
                directEntity, causingEntity, position
            )
        }

    }

}