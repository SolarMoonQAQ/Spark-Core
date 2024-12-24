package cn.solarmoon.spark_core.entry_builder.common

import net.neoforged.neoforge.attachment.AttachmentType
import net.neoforged.neoforge.attachment.IAttachmentHolder
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

class AttachmentBuilder<A>(private val attachmentDeferredRegister: DeferredRegister<AttachmentType<*>>) {

    private var id: String = ""
    private var builder: AttachmentType.Builder<A>? = null

    fun id(id: String) = apply { this.id = id }

    /**
     * 为空时返回的默认值，必填
     */
    fun defaultValue(value: (IAttachmentHolder) -> A) = apply {
        this.builder = AttachmentType.builder(value)
    }

    /**
     * 编码器，如果需要存储则需填入，选填
     */
    fun serializer(builderSerializer: (AttachmentType.Builder<A>) -> Unit) = apply { builderSerializer.invoke(builder!!) }

    fun build(): DeferredHolder<AttachmentType<*>, AttachmentType<A>> = attachmentDeferredRegister.register(id,
        Supplier { builder!!.build() }
    )

}