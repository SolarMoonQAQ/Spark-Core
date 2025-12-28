package cn.solarmoon.spark_core.entry_builder.common

import cn.solarmoon.spark_core.entry_builder.RegisterBuilder
import com.mojang.serialization.Codec
import net.neoforged.neoforge.attachment.AttachmentType
import net.neoforged.neoforge.attachment.IAttachmentHolder
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class AttachmentBuilder<A>(
    deferredRegister: DeferredRegister<AttachmentType<*>>
) : RegisterBuilder<AttachmentType<*>, AttachmentType<A>>(deferredRegister) {

    lateinit var factory: (IAttachmentHolder) -> A
    var serializer: Codec<A>? = null
    var shouldSerialize = { a: A -> true }
    var copyOnDeath: Boolean = false // 新增：控制是否在死亡时自动复制

    override fun build(): DeferredHolder<AttachmentType<*>, AttachmentType<A>> {
        return deferredRegister.register(id, Supplier {
            // 创建构建器并设置基础工厂
            val builder = AttachmentType.builder(factory)

            // 如果有序列化器，则设置序列化
            serializer?.let {
                builder.serialize(it, shouldSerialize)
            }

            // 如果设置了copyOnDeath，则调用对应方法
            if (copyOnDeath) {
                builder.copyOnDeath()
            }

            // 构建最终的AttachmentType
            builder.build()
        })
    }

}