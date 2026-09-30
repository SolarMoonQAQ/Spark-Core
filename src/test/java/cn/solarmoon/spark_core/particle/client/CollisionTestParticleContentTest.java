package cn.solarmoon.spark_core.particle.client;

import cn.solarmoon.spark_core.particle.common.data.ParticleEffectDefinition;
import cn.solarmoon.spark_core.particle.common.data.component.appearance.ParticleAppearanceBillboard;
import cn.solarmoon.spark_core.particle.common.data.component.motion.ParticleMotionCollision;
import cn.solarmoon.spark_core.particle.common.data.event.EventNode;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 内容级校验：默认内容包里的方块碰撞测试粒子
 * （{@code spark_modules/spark_core.default/minecraft/particles/collision_test.particle.json}）
 * 必须能被完整反序列化，且碰撞参数与事件节点与设计一致。
 * <p>
 * 该文件是 §7.1 手动场景的载体，因此这里把「设计约定」钉成断言：
 * 世界空间（碰撞组件必须真的被创建）、视觉与碰撞盒等大、`events` 数组格式 + 两级 `min_speed`。
 */
class CollisionTestParticleContentTest {

    private static final String RESOURCE_PATH =
            "/spark_modules/spark_core.default/minecraft/particles/collision_test.particle.json";
    private static final String EFFECT_ID = "minecraft:collision_test";
    private static final float RADIUS = 0.15f;
    private static final float BILLBOARD_SIZE = 0.5f;

    private static ParticleEffectDefinition load() throws Exception {
        try (InputStream in = CollisionTestParticleContentTest.class.getResourceAsStream(RESOURCE_PATH)) {
            assertNotNull(in, "内容包资源缺失: " + RESOURCE_PATH);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return ParticleEffectDeserializer.deserialize(ResourceLocation.parse(EFFECT_ID),
                    JsonParser.parseString(json));
        }
    }

    private static ParticleMotionCollision collisionDef(ParticleEffectDefinition def) {
        return def.getParticlePreset().getDefinitions().stream()
                .filter(ParticleMotionCollision.class::isInstance)
                .map(ParticleMotionCollision.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("碰撞组件未进入定义列表"));
    }

    /** 世界空间预设 → 碰撞运行时组件必须被创建（否则整条测试链路是空跑） */
    @Test
    void createsCollisionRuntimeComponent() throws Exception {
        ParticleEffectDefinition def = load();
        assertEquals(EFFECT_ID, def.getIdentifier().toString());
        assertEquals(def.getParticlePreset().getDefinitions().size(),
                def.getParticlePreset().getAllComponents().length,
                "每个粒子组件定义都应产出对应的运行时组件");
    }

    /** 碰撞字段按设计即可观察：弹跳（restitution>0）+ 衰减（drag）+ 不因接触消失 */
    @Test
    void collisionFieldsMatchTheObservableDesign() throws Exception {
        ParticleMotionCollision c = collisionDef(load());
        assertEquals(String.valueOf(RADIUS), c.getCollisionRadius());
        assertEquals("1.5", c.getCollisionDrag());
        assertEquals("0.6", c.getCoefficientOfRestitution());
        assertFalse(c.isExpireOnContact(), "落地不应消失，否则看不到静止与去重");
        assertEquals(List.of(new ParticleMotionCollision.CollisionEvent("collision_hit", 2f),
                             new ParticleMotionCollision.CollisionEvent("collision_hard", 8f)),
                     c.getEvents());
    }

    /** events 用的是**数组**格式（旧实现会在此抛异常并让整个定义加载失败）+ 两级 min_speed 各有事件节点 */
    @Test
    void arrayFormEventsAreWiredToLogAndSoundNodes() throws Exception {
        Map<String, List<EventNode>> events = load().getEvents();

        List<EventNode> hit = events.get("collision_hit");
        assertNotNull(hit, "缺少 collision_hit 事件");
        assertEquals(2, hit.size());
        assertInstanceOf(EventNode.LogEvent.class, hit.get(0));
        assertEquals("[collision_test] 碰撞（反弹前 |v| ≥ 2 格/秒）",
                ((EventNode.LogEvent) hit.get(0)).getMessage());
        assertInstanceOf(EventNode.SoundEffectEvent.class, hit.get(1));
        assertEquals("minecraft:block.stone.hit",
                ((EventNode.SoundEffectEvent) hit.get(1)).getSound());

        List<EventNode> hard = events.get("collision_hard");
        assertNotNull(hard, "缺少 collision_hard 事件");
        assertEquals(1, hard.size());
        assertInstanceOf(EventNode.LogEvent.class, hard.getFirst());
    }

    /**
     * 视觉半边长 == 碰撞盒半边长（0.1 + radius）。
     * 这样静止时贴图正好落在方块表面上，肉眼即可判断「有没有陷入方块」；
     * 一旦有人改了 size 或 collision_radius，本断言会提醒两者不再重合。
     */
    @Test
    void visualHalfSizeMatchesCollisionBoxHalfExtent() throws Exception {
        ParticleAppearanceBillboard billboard = load().getParticlePreset().getDefinitions().stream()
                .filter(ParticleAppearanceBillboard.class::isInstance)
                .map(ParticleAppearanceBillboard.class::cast)
                .findFirst()
                .orElseThrow();
        float visualHalf = Float.parseFloat(billboard.getSize()[0]) / 2f;
        assertEquals(BILLBOARD_SIZE / 2f, visualHalf, 1e-6f);
        assertEquals(CollisionResolver.halfExtent(RADIUS), visualHalf, 1e-6f,
                "视觉半边长应与碰撞盒半边长（0.1 + radius）一致");
    }
}
