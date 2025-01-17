package cn.solarmoon.spark_core.entry_builder

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.entry_builder.client.KeyMappingBuilder
import cn.solarmoon.spark_core.entry_builder.client.LayerBuilder
import cn.solarmoon.spark_core.entry_builder.common.*
import cn.solarmoon.spark_core.entry_builder.common.fluid.FluidBuilder
import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.util.RegisterUtil
import net.minecraft.core.RegistrySetBuilder
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.Item
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.levelgen.feature.Feature
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider
import net.neoforged.neoforge.data.event.GatherDataEvent
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NeoForgeRegistries


class ObjectRegister(val modId: String, val gatherData: Boolean = true) {

    var modBus: IEventBus? = null

    val itemDeferredRegister = DeferredRegister.create(Registries.ITEM, modId)
    val blockDeferredRegister = DeferredRegister.create(Registries.BLOCK, modId)
    val blockEntityDeferredRegister = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, modId)
    val fluidDeferredRegister = DeferredRegister.create(Registries.FLUID, modId)
    val fluidTypeDeferredRegister = DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, modId)
    val attachmentDeferredRegister = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, modId)
    val featureDeferredRegister = DeferredRegister.create(Registries.FEATURE, modId)
    val attributeDeferredRegister = DeferredRegister.create(Registries.ATTRIBUTE, modId)
    val creativeTabDeferredRegister = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, modId)
    val effectDeferredRegister = DeferredRegister.create(Registries.MOB_EFFECT, modId)
    val entityDeferredRegister = DeferredRegister.create(Registries.ENTITY_TYPE, modId)
    val particleDeferredRegister = DeferredRegister.create(Registries.PARTICLE_TYPE, modId)
    val recipeDeferredRegister = DeferredRegister.create(Registries.RECIPE_TYPE, modId)
    val recipeSerializerDeferredRegister = DeferredRegister.create(Registries.RECIPE_SERIALIZER, modId)
    val soundDeferredRegister = DeferredRegister.create(Registries.SOUND_EVENT, modId)
    val dataComponentDeferredRegister = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, modId)
    val entityDataDeferredRegister = DeferredRegister.create(NeoForgeRegistries.ENTITY_DATA_SERIALIZERS, modId)
    val skillTypeDeferredRegister = lazy { DeferredRegister.create(SparkRegistries.SKILL_TYPE, modId) }
    val typedAnimationDeferredRegister = lazy { DeferredRegister.create(SparkRegistries.TYPED_ANIMATION, modId) }

    fun register(bus: IEventBus) {
        modBus = bus
        itemDeferredRegister.register(bus)
        blockDeferredRegister.register(bus)
        blockEntityDeferredRegister.register(bus)
        fluidDeferredRegister.register(bus)
        fluidTypeDeferredRegister.register(bus)
        attachmentDeferredRegister.register(bus)
        featureDeferredRegister.register(bus)
        attributeDeferredRegister.register(bus)
        creativeTabDeferredRegister.register(bus)
        effectDeferredRegister.register(bus)
        entityDeferredRegister.register(bus)
        particleDeferredRegister.register(bus)
        recipeDeferredRegister.register(bus)
        recipeSerializerDeferredRegister.register(bus)
        soundDeferredRegister.register(bus)
        dataComponentDeferredRegister.register(bus)
        entityDataDeferredRegister.register(bus)
        modBus!!.takeIf { gatherData }?.addListener(this::gather)
    }

    private fun gather(event: GatherDataEvent) {
        val generator = event.generator
        val output = generator.packOutput
        val lookupProvider = event.lookupProvider
        val builder = RegistrySetBuilder()
        RegisterUtil.gatherBuilderFix(builder)
        builder.add(Registries.PLACED_FEATURE, FeatureBuilder.Gather::placedBootStrap)
        generator.addProvider(
            event.includeServer(),
            DatapackBuiltinEntriesProvider(output, lookupProvider, builder, mutableSetOf(modId))
        )
    }

    /**
     * @throws java.lang.NullPointerException 该类型的注册必须放在所有自定义注册类型之前
     */
    fun <T> registry() = RegistryBuilder<T>(modId, modBus!!)
    fun <I: Item> item() = ItemBuilder<I>(itemDeferredRegister, modBus!!)
    fun <B: Block> block() = BlockBuilder<B>(blockDeferredRegister)
    fun <B: BlockEntity> blockentity() = BlockEntityBuilder<B>(blockEntityDeferredRegister, modBus!!)
    fun <E: Entity> entityType() = EntityTypeBuilder<E>(entityDeferredRegister)
    fun fluid() = FluidBuilder(
        modId,
        fluidDeferredRegister,
        fluidTypeDeferredRegister,
        blockDeferredRegister,
        itemDeferredRegister,
        modBus!!
    )
    fun <A> attachment() = AttachmentBuilder<A>(attachmentDeferredRegister)
    fun <D> dataComponent() = DataComponentBuilder<D>(dataComponentDeferredRegister)
    fun <C: FeatureConfiguration, F: Feature<C>> feature() = FeatureBuilder<C, F>(modId, featureDeferredRegister)
    fun attribute() = AttributeBuilder(modId, attributeDeferredRegister, modBus!!)
    fun creativeTab() = CreativeTabBuilder(creativeTabDeferredRegister)
    fun damageType() = DamageTypeBuilder(modId)
    fun effect() = EffectBuilder(effectDeferredRegister)
    fun <P: ParticleType<*>> particle() = ParticleBuilder<P>(particleDeferredRegister)
    fun <R: Recipe<*>> recipe() = RecipeBuilder<R>(modId, recipeSerializerDeferredRegister, recipeDeferredRegister)
    fun sound() = SoundBuilder(modId, soundDeferredRegister)
    fun <D> entityData() = EntityDataBuilder<D>(entityDataDeferredRegister)
    fun layer() = LayerBuilder(modId, modBus!!)
    fun keyMapping() = KeyMappingBuilder(modId, modBus!!)

    fun <T, S: Skill<T>> skillType(): SkillTypeBuilder<T, S> {
        if (!skillTypeDeferredRegister.isInitialized()) {
            skillTypeDeferredRegister.value.register(modBus!!)
        }
        return SkillTypeBuilder(modId, skillTypeDeferredRegister.value)
    }

    fun typedAnimation(): TypedAnimationBuilder {
        if (!typedAnimationDeferredRegister.isInitialized()) {
            typedAnimationDeferredRegister.value.register(modBus!!)
        }
        return TypedAnimationBuilder(typedAnimationDeferredRegister.value)
    }

}