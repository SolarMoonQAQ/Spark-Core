package cn.solarmoon.spark_core.particle.client;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 内容级回归：默认内容包里**每一个** {@code .particle.json} 都必须能反序列化成功。
 * <p>
 * 加载失败的表现是「该粒子效果整体缺失 + 一条 error 日志」（内容包模块吞掉异常），
 * 因此这里逐个文件跑完整的 {@link ParticleEffectDeserializer}，把静默失败变成红灯。
 */
class ContentParticleLoadTest {

    private static final String CONTENT_ROOT = "/spark_modules/spark_core.default/minecraft/particles";

    /**
     * 允许加载失败的预设 + 原因。集合必须**恰好**等于实际失败集合：
     * 变大说明有新回归；变小说明修好了（同时删掉这里的条目）。
     * <p>
     * 当前为空 —— 内容包里的 7 个预设全部可加载。
     * （`rain`/`snow` 曾因 `uv.uv` 是 MoLang 表达式而无法加载，已由
     * {@code ParticleAppearanceBillboard.readFloatArray()} 的容错降级修复；
     * 完整的逐粒子 UV 表达式求值仍见 particle_soa_plan.md §14.5。）
     */
    private static final Map<String, String> KNOWN_UNLOADABLE = Map.of();

    private static List<Path> contentFiles() throws Exception {
        URL url = ContentParticleLoadTest.class.getResource(CONTENT_ROOT);
        assertNotNull(url, "内容目录不存在: " + CONTENT_ROOT);
        try (Stream<Path> walk = Files.walk(Path.of(url.toURI()))) {
            return walk.filter(p -> p.getFileName().toString().endsWith(".particle.json")).sorted().toList();
        }
    }

    @Test
    void everyBundledPresetDeserializes() throws Exception {
        List<Path> files = contentFiles();
        assertFalse(files.isEmpty(), "内容包里没有找到任何粒子定义");

        Map<String, String> failures = new TreeMap<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            String json = Files.readString(file, StandardCharsets.UTF_8);
            String id = "test:" + name.replace(".particle.json", "");
            try {
                ParticleEffectDeserializer.deserialize(ResourceLocation.parse(id), JsonParser.parseString(json));
            } catch (Throwable t) {
                failures.put(name, t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
        assertEquals(KNOWN_UNLOADABLE.keySet(), failures.keySet(),
                "加载失败的预设集合与登记不符（变小=修好了，需同步删登记；变大=新回归）:\n  "
                        + String.join("\n  ", failures.entrySet().stream()
                                .map(e -> e.getKey() + " → " + e.getValue()).toList()));
    }

    /** 每个能加载的预设都必须真的产出粒子级运行时组件，避免「加载成功但什么都没解析出来」 */
    @Test
    void loadedPresetsYieldParticleComponents() throws Exception {
        List<String> empty = new ArrayList<>();
        for (Path file : contentFiles()) {
            String name = file.getFileName().toString();
            if (KNOWN_UNLOADABLE.containsKey(name)) continue;
            var def = ParticleEffectDeserializer.deserialize(
                    ResourceLocation.parse("test:" + name.replace(".particle.json", "")),
                    JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)));
            if (def.getParticlePreset().getAllComponents().length == 0) empty.add(name);
        }
        assertTrue(empty.isEmpty(), "以下预设没有解析出任何粒子运行时组件: " + empty);
    }
}
