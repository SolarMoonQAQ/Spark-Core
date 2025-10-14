package cn.solarmoon.spark_core.entry_builder

import com.mojang.serialization.MapCodec
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleType
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeType
import net.neoforged.neoforge.registries.DeferredRegister

fun <M, N: M> DeferredRegister<M>.toCommonBuilder() = CommonRegisterBuilder<M, N>(this)

fun <O: ParticleOptions> RegisterBuilder<ParticleType<*>, ParticleType<O>>.createWithCodec(ignoreDistance: Boolean, codec: (ParticleType<O>) -> MapCodec<O>, streamCodec: (ParticleType<O>) -> StreamCodec<in RegistryFriendlyByteBuf, O>) =
    object: ParticleType<O>(ignoreDistance) {
        override fun codec(): MapCodec<O> = codec(this)

        override fun streamCodec(): StreamCodec<in RegistryFriendlyByteBuf, O> = streamCodec(this)
    }

fun <R: Recipe<*>> RegisterBuilder<RecipeType<*>, RecipeType<R>>.simpleType() = RecipeType.simple<R>(ResourceLocation.fromNamespaceAndPath(modId, id))

fun <D> RegisterBuilder<DataComponentType<*>, DataComponentType<D>>.dataComponentBuilder(builder: DataComponentType.Builder<D>.() -> Unit) =
    { DataComponentType.Builder<D>().also { builder(it) }.build() }

fun <E: Entity> RegisterBuilder<EntityType<*>, EntityType<E>>.entityTypeBuilder(factory: EntityType.EntityFactory<E>, category: MobCategory, builder: EntityType.Builder<E>.() -> Unit = {}) =
    { EntityType.Builder.of(factory, category).also { builder(it) }.build(id) }