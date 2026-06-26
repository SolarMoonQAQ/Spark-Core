package cn.solarmoon.sparkcore.minecraft.entry_builder.common

import cn.solarmoon.sparkcore.minecraft.entry_builder.RegisterBuilder
import com.mojang.serialization.Codec
import net.neoforged.neoforge.attachment.AttachmentType
import net.neoforged.neoforge.attachment.IAttachmentHolder
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class AttachmentBuilder<A>(
    deferredRegister: DeferredRegister<AttachmentType<*>>
): RegisterBuilder<AttachmentType<*>, AttachmentType<A>>(deferredRegister) {

    lateinit var factory: (IAttachmentHolder) -> A
    lateinit var serializer: Codec<A>
    var shouldSerialize = { a: A -> true }

    override fun build(): DeferredHolder<AttachmentType<*>, AttachmentType<A>> {
        return deferredRegister.register(id, Supplier { AttachmentType.builder(factory).serialize(serializer, shouldSerialize).build() })
    }

}