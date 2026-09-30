package cn.solarmoon.spark_core.particle.client;

import cn.solarmoon.spark_core.particle.common.data.ParticleEffectDefinition;
import cn.solarmoon.spark_core.particle.common.data.component.motion.ParticleMotionCollision;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 构建期碰撞门控的端到端测试（§4.6）：只有「在世界坐标中仿真」的预设才会创建碰撞组件。
 * <p>
 * 判定方式是「原始定义数 - 运行时组件数」：碰撞组件是唯一会在构建期被丢弃的组件，
 * 因此世界空间预设两者数量相等，被门控的预设少一个。
 */
class ParticleCollisionGatingTest {

    private static ParticleEffectDefinition load(String id, String components) {
        String json = """
                { "particle_effect": {
                    "description": { "identifier": "%s" },
                    "components": { %s }
                } }
                """.formatted(id, components);
        return ParticleEffectDeserializer.deserialize(ResourceLocation.parse(id), JsonParser.parseString(json));
    }

    private static final String COLLISION = """
            "minecraft:particle_motion_collision": {
                "collision_radius": 0.1,
                "events": [ { "event": "hit", "min_speed": 3 }, { "event": "splash" } ]
            }
            """;

    private static final String BILLBOARD = """
            "minecraft:particle_appearance_billboard": { "size": [0.1, 0.1] }
            """;

    /** 世界空间预设：碰撞组件被创建 */
    @Test
    void worldSpacePresetGetsCollisionComponent() {
        ParticleEffectDefinition def = load("test:world_space", COLLISION + "," + BILLBOARD);
        assertEquals(2, def.getParticlePreset().getDefinitions().size());
        assertEquals(2, def.getParticlePreset().getAllComponents().length,
                "世界空间预设应创建碰撞运行时组件");
        assertEquals(2, def.getParticlePreset().getCollisionEvents().size());
    }

    /** 局部空间（emitter_local_space.position=true）预设：碰撞组件被门控掉 */
    @Test
    void localSpacePresetDoesNotGetCollisionComponent() {
        ParticleEffectDefinition def = load("test:local_space", """
                "minecraft:emitter_local_space": { "position": true, "rotation": true },
                """ + COLLISION + "," + BILLBOARD);
        assertTrue(def.getEmitterPreset().hasLocalPosition());
        assertEquals(2, def.getParticlePreset().getDefinitions().size());
        assertEquals(1, def.getParticlePreset().getAllComponents().length,
                "局部空间预设不应创建碰撞运行时组件");
        // 事件仍被解析出来（只是永远不会触发），不影响既有 JSON 的加载
        assertEquals(2, def.getParticlePreset().getCollisionEvents().size());
    }

    /** 脚本粒子（particle_motion_parametric）预设：碰撞组件被门控掉 */
    @Test
    void parametricPresetDoesNotGetCollisionComponent() {
        ParticleEffectDefinition def = load("test:parametric", """
                "minecraft:particle_motion_parametric": {
                    "relative_position": ["0", "0", "0"]
                },
                """ + COLLISION + "," + BILLBOARD);
        assertFalse(def.getEmitterPreset().hasLocalPosition());
        assertEquals(3, def.getParticlePreset().getDefinitions().size());
        assertEquals(2, def.getParticlePreset().getAllComponents().length,
                "parametric 预设应保留 parametric 组件、丢弃碰撞组件");
        assertTrue(def.getParticlePreset().getDefinitions().stream()
                        .anyMatch(d -> d instanceof ParticleMotionCollision));
    }

    /** 非局部空间的 emitter_local_space（position 缺省 false）不触发门控 */
    @Test
    void localSpaceWithoutPositionStillCollides() {
        ParticleEffectDefinition def = load("test:local_rotation_only", """
                "minecraft:emitter_local_space": { "velocity": true },
                """ + COLLISION + "," + BILLBOARD);
        assertFalse(def.getEmitterPreset().hasLocalPosition());
        assertEquals(2, def.getParticlePreset().getAllComponents().length);
    }

    /** events 的数组/对象两种写法都能穿过完整反序列化管线 */
    @Test
    void eventsSurviveFullDeserialization() {
        ParticleEffectDefinition def = load("test:events_object", BILLBOARD + """
                , "minecraft:particle_motion_collision": {
                    "events": { "event": "hit" },
                    "expire_on_contact": true
                }
                """);
        var events = def.getParticlePreset().getCollisionEvents();
        assertEquals(1, events.size());
        assertEquals("hit", events.getFirst().event());
        assertEquals(2f, events.getFirst().minSpeed(), 0f);
        ParticleMotionCollision def0 = def.getParticlePreset().getDefinitions().stream()
                .filter(ParticleMotionCollision.class::isInstance)
                .map(ParticleMotionCollision.class::cast)
                .findFirst().orElseThrow();
        assertTrue(def0.isExpireOnContact());
    }
}
