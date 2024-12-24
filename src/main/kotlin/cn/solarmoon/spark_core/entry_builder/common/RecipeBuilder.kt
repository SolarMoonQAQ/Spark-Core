package cn.solarmoon.spark_core.entry_builder.common

import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.RecipeType
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class RecipeBuilder<R: Recipe<*>>(
    private val modId: String,
    private val recipeSerializerDeferredRegister: DeferredRegister<RecipeSerializer<*>>,
    private val recipeDeferredRegister: DeferredRegister<RecipeType<*>>
) {//

    private var id = ""
    private var serializerSupplier: Supplier<RecipeSerializer<R>>? = null

    fun id(id: String) = apply { this.id = id }

    fun serializer(serializerSupplier: Supplier<RecipeSerializer<R>>) = apply { this.serializerSupplier = serializerSupplier }

    fun build(): RecipeEntry<R> {
        val serializer = recipeSerializerDeferredRegister.register(id, serializerSupplier!!)
        val type: DeferredHolder<RecipeType<*>, RecipeType<R>> = recipeDeferredRegister.register(id, this::getRecipeType)
        return RecipeEntry(serializer, type)
    }

    class RecipeEntry<R: Recipe<*>>(
        val serializer: DeferredHolder<RecipeSerializer<*>, RecipeSerializer<R>>,
        val type: DeferredHolder<RecipeType<*>, RecipeType<R>>
    )

    private fun <T : Recipe<*>?> getRecipeType(): RecipeType<T> {
        return object : RecipeType<T> {
            override fun toString(): String {
                return "$modId:$id"
            }
        }
    }

}