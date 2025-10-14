package cn.solarmoon.spark_core.entry_builder

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.entry_builder.client.KeyMappingBuilder
import cn.solarmoon.spark_core.entry_builder.common.*
import cn.solarmoon.spark_core.gas.Ability
import cn.solarmoon.spark_core.gas.AbilitySpec
import cn.solarmoon.spark_core.gas.AbilityType
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.sync.Syncer
import cn.solarmoon.spark_core.sync.SyncerType
import com.mojang.serialization.MapCodec
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.registries.Registries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.syncher.EntityDataSerializer
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.monster.Zombie
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BedBlockEntity
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.attachment.AttachmentType
import net.neoforged.neoforge.capabilities.ItemCapability
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NeoForgeRegistries
import net.neoforged.neoforge.registries.NewRegistryEvent
import net.neoforged.neoforge.registries.RegistryBuilder


class ObjectRegister(
    val modId: String
) {

    lateinit var bus: IEventBus

    val item = registry { Registries.ITEM }
    val block = registry { Registries.BLOCK }
    val blockEntityType = registry { Registries.BLOCK_ENTITY_TYPE }
    val entityType = registry { Registries.ENTITY_TYPE }
    val attribute = registry { Registries.ATTRIBUTE }
    val dataComponentType = registry { Registries.DATA_COMPONENT_TYPE }
    val mobEffect = registry { Registries.MOB_EFFECT }
    val particleType = registry { Registries.PARTICLE_TYPE }
    val recipeType = registry { Registries.RECIPE_TYPE }
    val recipeSerializer = registry { Registries.RECIPE_SERIALIZER }
    val creativeModeTab = registry { Registries.CREATIVE_MODE_TAB }
    val soundEvent = registry { Registries.SOUND_EVENT }

    val attachment = registry { NeoForgeRegistries.ATTACHMENT_TYPES.key() }
    val entityDataSerializer = registry { NeoForgeRegistries.ENTITY_DATA_SERIALIZERS.key() }

    val syncerType = registry { SparkRegistries.SYNCER_TYPE.key() }

    protected fun <T> registry(key: () -> ResourceKey<out Registry<T>>) = lazy {
        DeferredRegister.create(key(), modId).apply {
            register(bus)
        }
    }

    fun register(bus: IEventBus) {
        this.bus = bus
    }

    fun <R> registry(id: String, builderConfigurator: (RegistryBuilder<R>) -> Registry<R>) = builderConfigurator(RegistryBuilder(ResourceKey.createRegistryKey<R>(ResourceLocation.fromNamespaceAndPath(modId, id)))).apply {
        bus.addListener { event: NewRegistryEvent -> event.register(this) }
    }

    // 一般
    fun <I: Item> item(builder: ItemBuilder<I>.() -> Unit) = ItemBuilder<I>(item.value, bus).also { builder(it) }.build()
    fun <B: Block> block(builder: CommonRegisterBuilder<Block, B>.() -> Unit) = commonBuilder(block.value, builder)
    fun <B: BlockEntity> blockEntityType(builder: BlockEntityTypeBuilder<B>.() -> Unit) = BlockEntityTypeBuilder<B>(blockEntityType.value, bus).also { builder(it) }.build()
    fun <E: Entity> entityType(builder: CommonRegisterBuilder<EntityType<*>, EntityType<E>>.() -> Unit) = commonBuilder(entityType.value, builder)
    fun <A: Attribute> attribute(builder: AttributeBuilder<A>.() -> Unit) = AttributeBuilder<A>(attribute.value, bus).also { builder(it) }.build()
    fun <D> dataComponentType(builder: CommonRegisterBuilder<DataComponentType<*>, DataComponentType<D>>.() -> Unit) = commonBuilder(dataComponentType.value, builder)
    fun <E: MobEffect> mobEffect(builder: CommonRegisterBuilder<MobEffect, E>.() -> Unit) = commonBuilder(mobEffect.value, builder)
    fun <O: ParticleOptions> particleType(builder: CommonRegisterBuilder<ParticleType<*>, ParticleType<O>>.() -> Unit) = commonBuilder(particleType.value, builder)
    fun <R: Recipe<*>> recipeType(builder: CommonRegisterBuilder<RecipeType<*>, RecipeType<R>>.() -> Unit) = commonBuilder(recipeType.value, builder)
    fun <R: Recipe<*>> recipeSerializer(builder: CommonRegisterBuilder<RecipeSerializer<*>, RecipeSerializer<R>>.() -> Unit) = commonBuilder(recipeSerializer.value, builder)
    fun creativeModeTab(builder: CommonRegisterBuilder<CreativeModeTab, CreativeModeTab>.() -> Unit) = commonBuilder(creativeModeTab.value, builder)
    fun soundEvent(builder: CommonRegisterBuilder<SoundEvent, SoundEvent>.() -> Unit) = commonBuilder(soundEvent.value, builder)

    // 客户端
    fun keyMapping(builder: (KeyMappingBuilder) -> Unit) = KeyMappingBuilder(modId, bus).also { builder(it) }.build()

    // 数据
    fun damageType(id: String) = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(modId, id))

    // neoforge
    fun <A> attachment(builder: CommonRegisterBuilder<AttachmentType<*>, AttachmentType<A>>.() -> Unit) = commonBuilder(attachment.value, builder)
    fun <D> entityDataSerializer(builder: CommonRegisterBuilder<EntityDataSerializer<*>, EntityDataSerializer<D>>.() -> Unit) = commonBuilder(entityDataSerializer.value,  builder)
    inline fun <reified T, reified O: Any?> itemCapability(id: String) = ItemCapability.create(ResourceLocation.fromNamespaceAndPath(modId, id), T::class.java, O::class.java)

    // 星火
    fun <S: Syncer> syncerType(builder: CommonRegisterBuilder<SyncerType<*>, SyncerType<S>>.() -> Unit) = commonBuilder(syncerType.value, builder)

    protected fun <M, N: M> commonBuilder(deferredRegister: DeferredRegister<M>, builder: CommonRegisterBuilder<M, N>.() -> Unit) = deferredRegister.toCommonBuilder<M, N>().also { builder(it) }.build()

}