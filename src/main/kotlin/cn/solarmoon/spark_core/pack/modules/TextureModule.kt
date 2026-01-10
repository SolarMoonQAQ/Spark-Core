package cn.solarmoon.spark_core.pack.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.pack.SparkPackLoaderApplier
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.neoforged.fml.loading.FMLEnvironment

class TextureModule : SparkPackModule {

    override val id: String = "textures"
    override val mode: ReadMode = ReadMode.LOCAL_ONLY

    var count = 0
    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if ((fromServer && isClientSide) || (!fromServer && !isClientSide)) return
        if (FMLEnvironment.dist.isClient) {
            count = 0
            SparkCore.LOGGER.info("开始注册外部包贴图资源…")
        }
    }

    override fun read(
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage,
        isClientSide: Boolean,
        fromServer: Boolean
    ) {
        if (!FMLEnvironment.dist.isClient) return
        if (!fileName.endsWith(".png")) return

        val namespace =
            pathSegments.firstOrNull() ?: SparkCore.MOD_ID

        val path =
            if (pathSegments.size >= 2)
                "textures/${pathSegments.drop(1).joinToString("/")}/$fileName"
            else
                "textures/$fileName"

        SparkPackLoaderApplier.CLIENT_PACK.put(
            PackType.CLIENT_RESOURCES,
            ResourceLocation.fromNamespaceAndPath(namespace, path),
            content
        )
        count++
    }



    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        if (FMLEnvironment.dist.isClient) {
            SparkCore.LOGGER.info("从外部包注册了{}张贴图资源", count)
        }
    }

}