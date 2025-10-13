package cn.solarmoon.spark_core.entry_builder

import cn.solarmoon.spark_core.entry_builder.client.KeyMappingBuilder
import cn.solarmoon.spark_core.entry_builder.common.*
import cn.solarmoon.spark_core.gas.Ability
import cn.solarmoon.spark_core.gas.AbilitySpec
import cn.solarmoon.spark_core.gas.AbilitySpecType
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
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.block.Block
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
    val abilityType = registry { SparkRegistries.ABILITY_TYPE.key() }
    val abilitySpecType = registry { SparkRegistries.ABILITY_SPEC_TYPE.key() }

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
    fun <B: Block> block(builder: RegisterBuilder<Block, B>.() -> Unit) = block.value.toCommonBuilder<Block, B>().also { builder(it) }.build()
    fun <B: BlockEntity> blockEntityType(builder: BlockEntityTypeBuilder<B>.() -> Unit) = BlockEntityTypeBuilder<B>(blockEntityType.value, bus).also { builder(it) }.build()
    fun <E: Entity> entityType(builder: RegisterBuilder<EntityType<*>, EntityType<E>>.() -> Unit) = entityType.value.toCommonBuilder<EntityType<*>, EntityType<E>>().also { builder(it) }.build()
    fun <A: Attribute> attribute(builder: AttributeBuilder<A>.() -> Unit) = AttributeBuilder<A>(attribute.value, bus).also { builder(it) }.build()
    fun <D> dataComponentType(builder: RegisterBuilder<DataComponentType<*>, DataComponentType<D>>.() -> Unit) = dataComponentType.value.toCommonBuilder<DataComponentType<*>, DataComponentType<D>>().also { builder(it) }.build()
    fun <E: MobEffect> mobEffect(builder: RegisterBuilder<MobEffect, E>.() -> Unit) = mobEffect.value.toCommonBuilder<MobEffect, E>().also { builder(it) }.build()
    fun <O: ParticleOptions> particleType(builder: RegisterBuilder<ParticleType<*>, ParticleType<O>>.() -> Unit) = particleType.value.toCommonBuilder<ParticleType<*>, ParticleType<O>>().also { builder(it) }.build()
    fun <R: Recipe<*>> recipeType(builder: RegisterBuilder<RecipeType<*>, RecipeType<R>>.() -> Unit) = recipeType.value.toCommonBuilder<RecipeType<*>, RecipeType<R>>().also { builder(it) }.build()
    fun <R: Recipe<*>> recipeSerializer(builder: RegisterBuilder<RecipeSerializer<*>, RecipeSerializer<R>>.() -> Unit) = recipeSerializer.value.toCommonBuilder<RecipeSerializer<*>, RecipeSerializer<R>>().also { builder(it) }.build()
    fun creativeModeTab(builder: RegisterBuilder<CreativeModeTab, CreativeModeTab>.() -> Unit) = creativeModeTab.value.toCommonBuilder().also { builder(it) }.build()
    fun soundEvent(builder: RegisterBuilder<SoundEvent, SoundEvent>.() -> Unit) = soundEvent.value.toCommonBuilder().also { builder(it) }.build()

    // 客户端
    fun keyMapping(builder: (KeyMappingBuilder) -> Unit) = KeyMappingBuilder(modId, bus).also { builder(it) }.build()

    // 数据
    fun damageType(id: String) = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(modId, id))

    // neoforge
    fun <A> attachment(builder: RegisterBuilder<AttachmentType<*>, AttachmentType<A>>.() -> Unit) = attachment.value.toCommonBuilder<AttachmentType<*>, AttachmentType<A>>().also { builder(it) }.build()
    fun <D> entityDataSerializer(builder: RegisterBuilder<EntityDataSerializer<*>, EntityDataSerializer<D>>.() -> Unit) = entityDataSerializer.value.toCommonBuilder<EntityDataSerializer<*>, EntityDataSerializer<D>>().also { builder(it) }.build()
    inline fun <reified T, reified O: Any?> itemCapability(id: String) = ItemCapability.create(ResourceLocation.fromNamespaceAndPath(modId, id), T::class.java, O::class.java)

    // 星火
    fun <A: Ability> abilityType(builder: (RegisterBuilder<AbilityType<*>, AbilityType<A>>) -> Unit) = abilityType.value.toCommonBuilder<AbilityType<*>, AbilityType<A>>().also { builder(it) }.build()

    fun <A: Ability, S: AbilitySpec<A>> abilitySpecType(builder: (RegisterBuilder<AbilitySpecType<*, *>, AbilitySpecType<A, S>>) -> Unit) = abilitySpecType.value.toCommonBuilder<AbilitySpecType<*, *>, AbilitySpecType<A, S>>().also { builder(it) }.build()
    fun <S: Syncer> syncerType(builder: RegisterBuilder<SyncerType<*>, SyncerType<S>>.() -> Unit) = syncerType.value.toCommonBuilder<SyncerType<*>, SyncerType<S>>().also { builder(it) }.build()
}

fun <M, N: M> DeferredRegister<M>.toCommonBuilder() = RegisterBuilder<M, N>(this)

fun <O: ParticleOptions> RegisterBuilder<ParticleType<*>, ParticleType<O>>.createWithCodec(ignoreDistance: Boolean, codec: (ParticleType<O>) -> MapCodec<O>, streamCodec: (ParticleType<O>) -> StreamCodec<in RegistryFriendlyByteBuf, O>) =
    object: ParticleType<O>(ignoreDistance) {
        override fun codec(): MapCodec<O> = codec(this)

        override fun streamCodec(): StreamCodec<in RegistryFriendlyByteBuf, O> = streamCodec(this)
    }

fun <R: Recipe<*>> RegisterBuilder<RecipeType<*>, RecipeType<R>>.simpleType() = RecipeType.simple<R>(ResourceLocation.fromNamespaceAndPath(modId, id))

fun <D> RegisterBuilder<DataComponentType<*>, DataComponentType<D>>.dataComponentBuilder(builder: DataComponentType.Builder<D>.() -> Unit) =
    { DataComponentType.Builder<D>().also { builder(it) }.build() }