package cn.solarmoon.spark_core.entry_builder.common

import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimProvider
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimation
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.registry.dynamic.DynamicAwareRegistry
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class TypedAnimationBuilder(private val register: DeferredRegister<TypedAnimation>) {

    private var id = ""
    private var index: AnimIndex? = null
    private var provider: TypedAnimProvider = {}
    private val animationRegistry = SparkRegistries.TYPED_ANIMATION

    /**
     * 设置动画ID
     *
     * @param id 动画ID（不包含命名空间）
     */
    fun id(id: String) = apply { this.id = id }

    /**
     * 设置完整的动画ID（包含命名空间）
     * 用于动态场景下的注册
     *
     * @param fullId 完整的资源位置
     */
    fun id(fullId: ResourceLocation) = apply { this.id = fullId.path }

    /**
     * 设置动画索引
     *
     * @param index 动画索引
     */
    fun animIndex(index: AnimIndex) = apply { this.index = index }


    fun provider(provider: TypedAnimProvider) = apply { this.provider = provider }

    /**
     * 构建并静态注册动画
     *
     * @return 注册的动画的 DeferredHolder
     */
    fun build() = register.register(id, Supplier { TypedAnimation(index!!, provider) })

    /**
     * 仅构造动画实例，不触发 DeferredRegister 注册
     * 用于动态注册场景
     *
     * @return 构造的 TypedAnimation 实例
     */
    fun constructOnly(): TypedAnimation {
        if (index == null) {
            throw IllegalStateException("AnimIndex must be set before constructing TypedAnimation")
        }
        return TypedAnimation(index!!, provider)
    }

    /**
     * 构造并在注册阶段过后动态注册
     *
     * @param fullId 完整的资源位置（包含命名空间）
     * @return 注册的动画实例
     */
    fun registerDynamic(fullId: ResourceLocation): TypedAnimation {
        val animation = constructOnly()
        val registry = SparkRegistries.TYPED_ANIMATION
        val resourceKey = net.minecraft.resources.ResourceKey.create(registry.key(), fullId)
        animationRegistry.register(resourceKey, animation, net.minecraft.core.RegistrationInfo.BUILT_IN)
        return animation
    }

}