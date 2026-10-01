package cn.solarmoon.spark_core.particle.client;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 贴图路径解析回归。
 * <p>
 * 历史缺陷：{@code basic_render_parameters.texture} 是**先无脑拼 {@code textures/}、再交给
 * ResourceLocation.parse()**，于是带命名空间引用的写法
 * {@code "machine_max:textures/particle/explosion_atlas"} 被拼成
 * {@code textures/machine_max:textures/particle/explosion_atlas.png}，
 * 冒号前的 {@code textures/machine_max} 成了含 {@code /} 的非法命名空间，
 * 整个粒子定义加载失败（表现为该效果在游戏内整体缺失）。
 * <p>
 * 断言口径与 {@code TextureModule} 的注册方式对齐：内容包里的
 * {@code <namespace>/particle/explosion_atlas.png} → {@code <namespace>:textures/particle/explosion_atlas.png}。
 */
class ParticleTexturePathTest {

    /** 取 JSON 里 {@code description.basic_render_parameters.texture} 解析出的资源位置 */
    private static ResourceLocation resolve(String textureValue) {
        var obj = JsonParser.parseString("{\"description\":{\"basic_render_parameters\":{\"texture\":\""
                + textureValue + "\"}}}");
        var desc = ParticleEffectDeserializer.deserialize(
                ResourceLocation.parse("test:probe"), obj).getDescription();
        return desc.getTexture();
    }

    @Test
    void namespacedTextureKeepsItsNamespace() {
        // 回归用例：Machine-Max 内容包 blast_medium/blast_small 的原始写法
        assertEquals(ResourceLocation.parse("machine_max:textures/particle/explosion_atlas.png"),
                resolve("machine_max:textures/particle/explosion_atlas"));
    }

    @Test
    void namespacedTextureAcceptsExtensionAndBarePath() {
        ResourceLocation expected = ResourceLocation.parse("machine_max:textures/particle/explosion_atlas.png");
        assertEquals(expected, resolve("machine_max:textures/particle/explosion_atlas.png"));

        // 没有 textures/ 前缀时只做字面补全，不会替作者补出 particle/ 这一级
        assertEquals(ResourceLocation.parse("machine_max:textures/explosion_atlas.png"),
                resolve("machine_max:explosion_atlas"));
        assertEquals(ResourceLocation.parse("machine_max:textures/particle/explosion_atlas.png"),
                resolve("machine_max:textures/particle/explosion_atlas"));
    }

    @Test
    void malformedNamespaceFallsBackToDefaultTexture() {
        // 模板变量/绝对路径前缀都不是合法命名空间：告警并退回默认贴图，不能硬失败
        ResourceLocation vanilla = ResourceLocation.parse("minecraft:textures/particle/particles.png");
        assertEquals(vanilla, resolve("{namespace}:textures/particle/smoke"));
        assertEquals(vanilla, resolve("textures/machine_max:textures/particle/explosion_atlas"));
    }

    @Test
    void unqualifiedPathsGoToMinecraft() {
        ResourceLocation vanilla = ResourceLocation.parse("minecraft:textures/particle/particles.png");
        assertEquals(vanilla, resolve("textures/particle/particles"));
        assertEquals(vanilla, resolve("textures/particle/particles.png"));
        assertEquals(vanilla, resolve("particle/particles"));
    }

    /**
     * 真实内容包回归：隔壁 Machine-Max 仓库里写的是命名空间引用，
     * 若内容包存在则必须能整份反序列化成功（路径不存在时跳过，保证 CI 可独立运行）。
     */
    @Test
    void machineMaxPackParticleLoads() throws Exception {
        Path particle = Path.of("..", "Machine-Max", "src", "main", "resources", "spark_modules",
                "machine_max.builtin", "machine_max", "particles", "blast_medium.particle.json");
        if (!Files.exists(particle)) {
            return; // 平级仓库不在，跳过
        }
        var def = ParticleEffectDeserializer.deserialize(
                ResourceLocation.parse("machine_max:blast_medium"),
                JsonParser.parseString(Files.readString(particle, StandardCharsets.UTF_8)));
        assertEquals(ResourceLocation.parse("machine_max:textures/particle/explosion_atlas.png"),
                def.getDescription().getTexture());
    }
}
