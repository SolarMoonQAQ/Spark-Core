package cn.solarmoon.spark_core.entry_builder.common

import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimProvider
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimation
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class TypedAnimationBuilder(private val register: DeferredRegister<TypedAnimation>) {

    private var id = ""
    private var index: AnimIndex? = null
    private var provider: TypedAnimProvider = {}

    fun id(id: String) = apply { this.id = id }

    fun animIndex(index: AnimIndex) = apply { this.index = index }

    fun provider(provider: TypedAnimProvider) = apply { this.provider = provider }

    fun build() = register.register(id, Supplier { TypedAnimation(index!!, provider) })

}