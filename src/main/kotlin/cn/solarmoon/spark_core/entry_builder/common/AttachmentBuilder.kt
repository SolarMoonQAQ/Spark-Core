package cn.solarmoon.spark_core.entry_builder.common

import cn.solarmoon.spark_core.entry_builder.RegisterBuilder
import com.mojang.serialization.Codec
import com.oracle.truffle.`object`.enterprise.a
import kotlinx.serialization.Serializer
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

    override fun build(): DeferredHolder<AttachmentType<*>, AttachmentType<A>> {
        return serializer?.let {
            deferredRegister.register(id, Supplier { AttachmentType.builder(factory).serialize(serializer!!, shouldSerialize).build() })
        } ?: deferredRegister.register(id, Supplier { AttachmentType.builder(factory).build() })
    }

}