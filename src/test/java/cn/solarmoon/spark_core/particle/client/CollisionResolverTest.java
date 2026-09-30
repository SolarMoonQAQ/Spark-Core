package cn.solarmoon.spark_core.particle.client;

import cn.solarmoon.spark_core.particle.common.data.component.motion.ParticleMotionCollision;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CollisionResolver / ParticleMotionCollision 的纯函数级测试。
 * <p>
 * 覆盖 §7.4 回归清单中的三项：bounce()、箱体尺寸与半径夹取、events 双格式解析，
 * 以及 P0 的验收前提——无碰撞快路径与旧 {@code ParticleArray.integrateParticle} 逐位一致。
 */
class CollisionResolverTest {

    private static final float TICK_DT = 0.05f;

    // ==================== bounce() ====================

    @Test
    void bounceKeepsDirectionOppositeAndScalesByRestitution() {
        // 4 - 1 = 3，乘 0.5 → 反向 1.5
        assertEquals(-1.5f, CollisionResolver.bounce(4f, 1f, 0.5f), 0f);
        assertEquals(1.5f, CollisionResolver.bounce(-4f, 1f, 0.5f), 0f);
    }

    @Test
    void bounceDoesNotReverseWhenDragExceedsSpeed() {
        // 下限做 0 截断：drag > |v| 时不得反向弹射
        assertEquals(0f, CollisionResolver.bounce(1f, 5f, 0.8f), 0f);
        assertEquals(0f, CollisionResolver.bounce(-1f, 5f, 0.8f), 0f);
    }

    @Test
    void bounceWithZeroRestitutionOrZeroSpeedIsZero() {
        assertEquals(0f, CollisionResolver.bounce(4f, 1f, 0f), 0f);
        assertEquals(0f, CollisionResolver.bounce(0f, 0f, 1f), 0f);
        assertEquals(0f, CollisionResolver.bounce(1f, 0f, 0f), 0f);
    }

    // ==================== 箱体尺寸与半径夹取 ====================

    @Test
    void halfExtentIsBaseHalfPlusRadius() {
        assertEquals(0.1f, CollisionResolver.halfExtent(0f), 0f);
        assertEquals(0.2f, CollisionResolver.halfExtent(0.1f), 1e-7f);
        assertEquals(0.5f, CollisionResolver.halfExtent(0.4f), 1e-7f);
    }

    @Test
    void halfExtentClampsRadiusToSpecLimitAndToZero() {
        // 规范上限 1/2 格 → 半边长最大 0.6
        assertEquals(0.6f, CollisionResolver.halfExtent(5f), 1e-7f);
        assertEquals(0.6f, CollisionResolver.halfExtent(0.5f), 1e-7f);
        // 负值与 NaN 不得把碰撞盒缩小到基础尺寸以下
        assertEquals(0.1f, CollisionResolver.halfExtent(-3f), 0f);
        assertEquals(0.1f, CollisionResolver.halfExtent(Float.NaN), 0f);
    }

    // ==================== events 双格式解析 ====================

    @Test
    void parsesEventsArrayForm() {
        var def = ParticleMotionCollision.fromJson(JsonParser.parseString("""
                { "events": [ { "event": "hit", "min_speed": 3.5 }, { "event": "splash" } ] }
                """).getAsJsonObject());
        assertEquals(List.of(new ParticleMotionCollision.CollisionEvent("hit", 3.5f),
                             new ParticleMotionCollision.CollisionEvent("splash", 2f)),
                     def.getEvents());
    }

    @Test
    void parsesEventsSingleObjectForm() {
        var def = ParticleMotionCollision.fromJson(JsonParser.parseString("""
                { "events": { "event": "hit", "min_speed": 3.5 } }
                """).getAsJsonObject());
        assertEquals(List.of(new ParticleMotionCollision.CollisionEvent("hit", 3.5f)), def.getEvents());
    }

    @Test
    void minSpeedDefaultsToTwoBlocksPerSecond() {
        var def = ParticleMotionCollision.fromJson(JsonParser.parseString("""
                { "events": { "event": "hit" } }
                """).getAsJsonObject());
        assertEquals(2f, def.getEvents().getFirst().minSpeed(), 0f);
    }

    @Test
    void invalidEventsTypesDegradeToEmptyWithoutThrowing() {
        // 旧实现用 getAsJsonObject() 解析，数组形式会抛 IllegalStateException 让整个定义加载失败
        assertDoesNotThrow(() -> ParticleMotionCollision.fromJson(JsonParser.parseString("""
                { "events": { "event": 123 } }
                """).getAsJsonObject()));
        var numeric = ParticleMotionCollision.fromJson(JsonParser.parseString("""
                { "events": 7 }
                """).getAsJsonObject());
        assertTrue(numeric.getEvents().isEmpty());
        assertTrue(numeric.isEventsFieldPresent());

        var missing = ParticleMotionCollision.fromJson(JsonParser.parseString("{}").getAsJsonObject());
        assertTrue(missing.getEvents().isEmpty());
        assertFalse(missing.isEventsFieldPresent());
    }

    @Test
    void parsesDefaultNumericFields() {
        var def = ParticleMotionCollision.fromJson(JsonParser.parseString("""
                { "enabled": "variable.flag", "collision_radius": 0.1, "collision_drag": "2 + 1",
                  "coefficient_of_restitution": 0.4, "expire_on_contact": true }
                """).getAsJsonObject());
        assertEquals("variable.flag", def.getEnabled());
        assertEquals("0.1", def.getCollisionRadius());
        assertEquals("2 + 1", def.getCollisionDrag());
        assertEquals("0.4", def.getCoefficientOfRestitution());
        assertTrue(def.isExpireOnContact());
        assertEquals(310, def.order());
    }

    // ==================== 无碰撞快路径逐位一致（P0 验收前提）====================

    @Test
    void integrationIsBitIdenticalToLegacyIntegrateParticle() {
        ParticleArray buf = new ParticleArray(16);
        int i = buf.allocate();
        buf.setPos(i, 1.5f, 64.25f, -3.75f);
        buf.setVel(i, 0.7f, -1.3f, 2.1f);
        buf.setAccel(i, 0.3f, -9.8f, -0.2f);
        buf.setDragCoeff(i, 0.45f);
        buf.setRot(i, 0.25f);
        buf.setRotRate(i, 1.5f);
        buf.setRotAccel(i, -0.3f);
        buf.setAge(i, 0.75f);
        buf.setMaxLifetime(i, 5f);

        float[][] expected = legacyIntegrate(buf, i, TICK_DT);

        // level == null → 只做无碰撞积分，不进入扫掠
        byte result = CollisionResolver.move(buf, i, TICK_DT, null, new float[1]);

        assertEquals(0, result, "无碰撞路径不应返回任何位标志");
        assertBitsEqual(expected[0][0], buf.getVelX(i), "velX");
        assertBitsEqual(expected[0][1], buf.getVelY(i), "velY");
        assertBitsEqual(expected[0][2], buf.getVelZ(i), "velZ");
        assertBitsEqual(expected[1][0], buf.getPosX(i), "posX");
        assertBitsEqual(expected[1][1], buf.getPosY(i), "posY");
        assertBitsEqual(expected[1][2], buf.getPosZ(i), "posZ");
        assertBitsEqual(expected[2][0], buf.getRot(i), "rot");
        assertBitsEqual(expected[2][1], buf.getRotRate(i), "rotRate");
        assertBitsEqual(expected[2][2], buf.getAge(i), "age");
    }

    @Test
    void deadParticleIsNotMoved() {
        ParticleArray buf = new ParticleArray(16);
        int i = buf.allocate();
        buf.setPos(i, 1f, 2f, 3f);
        buf.setVel(i, 1f, 1f, 1f);
        buf.markDead(i);

        assertEquals(0, CollisionResolver.move(buf, i, TICK_DT, null, new float[1]));
        assertEquals(1f, buf.getPosX(i), 0f);
        assertEquals(1f, buf.getVelX(i), 0f);
    }

    /**
     * 旧 {@code ParticleArray.integrateParticle} 的公式（已从生产代码删除，此处保留作为对照基线）。
     *
     * @return [速度, 位置, {rot, rotRate, age}]
     */
    private static float[][] legacyIntegrate(ParticleArray buf, int i, float tickDt) {
        float velX = buf.getVelX(i) + buf.getAccelX(i) * tickDt;
        float velY = buf.getVelY(i) + buf.getAccelY(i) * tickDt;
        float velZ = buf.getVelZ(i) + buf.getAccelZ(i) * tickDt;

        float dragFactor = Math.max(0, 1 - buf.getDragCoeff(i) * tickDt);
        velX *= dragFactor;
        velY *= dragFactor;
        velZ *= dragFactor;

        float posX = buf.getPosX(i) + velX * tickDt;
        float posY = buf.getPosY(i) + velY * tickDt;
        float posZ = buf.getPosZ(i) + velZ * tickDt;

        float rotRate = buf.getRotRate(i) + buf.getRotAccel(i) * tickDt;
        float rot = buf.getRot(i) + rotRate * tickDt;
        float age = buf.getAge(i) + tickDt;

        return new float[][]{{velX, velY, velZ}, {posX, posY, posZ}, {rot, rotRate, age}};
    }

    private static void assertBitsEqual(float expected, float actual, String name) {
        assertEquals(Integer.toHexString(Float.floatToIntBits(expected)),
                     Integer.toHexString(Float.floatToIntBits(actual)),
                     name + " 不是逐位一致");
    }
}
