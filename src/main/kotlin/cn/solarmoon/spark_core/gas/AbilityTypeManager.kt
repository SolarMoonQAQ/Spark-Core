package cn.solarmoon.spark_core.gas

import net.minecraft.resources.ResourceLocation

object AbilityTypeManager {

    private val abilityTypes = linkedMapOf<ResourceLocation, AbilityType<*>>()
    private val reverseLookup = mutableMapOf<AbilityType<*>, ResourceLocation>()

    val allAbilityTypes get() = abilityTypes.toMap()

    fun register(id: ResourceLocation, abilityType: AbilityType<*>) {
        val mId = abilityTypes.size
        abilityType.id = mId
        abilityTypes[id] = abilityType
        reverseLookup[abilityType] = id
    }

    fun getAbilityType(id: Int) = abilityTypes.values.elementAtOrNull(id)

    fun getAbilityType(id: ResourceLocation) = abilityTypes[id]

    fun getKey(abilityType: AbilityType<*>) = reverseLookup[abilityType]

    fun initialize() {
        abilityTypes.clear()
        reverseLookup.clear()
    }

}
