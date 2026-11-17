package cn.solarmoon.spark_core.particle.client.render;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/** 半透明视觉粒子不向世界深度写入实体遮挡；显式不透明材质保持深度写入。 */
class ParticleDepthPolicyTest {
    @Test void transparentParticlesWriteColorWithoutReplacingWorldDepth() throws Exception {
        var texture = ResourceLocation.parse("machine_max:textures/particle/policy_test.png");
        for (String material : new String[]{"particles_blend", "particles_add"}) {
            Object state = field(ParticleRenderType.fromMaterial(material, texture), "state");
            Object mask = field(state, "writeMaskState");
            assertEquals(true, field(mask, "writeColor"), material);
            assertEquals(false, field(mask, "writeDepth"), material);
        }
        Object opaque = ParticleRenderType.fromMaterial("particles_opaque", texture);
        Object state = field(opaque, "state");
        assertEquals(true, field(field(state, "writeMaskState"), "writeDepth"));
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
}
