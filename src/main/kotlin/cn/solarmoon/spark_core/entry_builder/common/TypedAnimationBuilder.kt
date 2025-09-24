package cn.solarmoon.spark_core.entry_builder.common

import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimProvider
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimation
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.util.normalize
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.core.RegistrationInfo
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class TypedAnimationBuilder(private val register: DeferredRegister<TypedAnimation>) {

    private var id = ""
    private var index: AnimIndex? = null
    private var provider: TypedAnimProvider = {}

    /**
     * 设置动画ID
     *
     * @param id 动画ID（不包含命名空间）
     */
    fun id(id: String) = apply { this.id = id }

    /**
     * 设置动画索引
     *
     * @param index 动画索引
     */
    fun animIndex(index: AnimIndex) = apply { this.index = index }

    fun provider(provider: TypedAnimProvider) = apply { this.provider = provider }

    fun build() = register.register(if (id.isEmpty()) index.toString().normalize() else id, Supplier { TypedAnimation(index!!, provider) })


}