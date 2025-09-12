package cn.solarmoon.spark_core.resource2.modules

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource2.graph.SparkPackage
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.loading.FMLEnvironment

class TextureModule : SparkPackModule {

    override val id: String = "textures"
    override val mode: ReadMode = ReadMode.LOCAL_ONLY


    var count = 0
    override fun onStart(isClientSide: Boolean) {
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
        isClientSide: Boolean
    ) {
        if (FMLEnvironment.dist.isClient && fileName.endsWith(".png")) {
            val nameSpace: String = if (pathSegments.size > 1) {
                pathSegments[0]
            } else {
                SparkCore.MOD_ID
            }
            val path: String = if (pathSegments.size >= 2) {
                "$id/${pathSegments.subList(1, pathSegments.size).joinToString("/")}/$fileName"
            } else {
                "$id/$fileName"
            }
            val image = NativeImage.read(content)
            val texture = DynamicTexture(image)
            Minecraft.getInstance().textureManager.register(
                ResourceLocation.fromNamespaceAndPath(nameSpace, path), texture
            )
            count++
        }
    }


    override fun onFinish(isClientSide: Boolean) {
        if (FMLEnvironment.dist.isClient) {
            SparkCore.LOGGER.info("从外部包注册了{}张贴图资源", count)
        }
    }

}