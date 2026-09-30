package cn.solarmoon.spark_core.particle.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 翻页书 UV / 帧号的纯函数测试。
 * <p>
 * 用内容包里 {@code magic.particle.json} 的真实参数（128×128 纹理、base_UV [64,96]、
 * size_UV [8,8]、step_UV [-8,0]、max_frame 8、stretch_to_lifetime）作为基准，
 * 覆盖两处修复：像素坐标必须按纹理尺寸归一化、{@code stretch_to_lifetime} 的帧号必须随寿命推进。
 */
class FlipbookUvTest {

    private static final float TEX = 128f;
    private static final float BASE_U = 64f, BASE_V = 96f;
    private static final float SIZE_X = 8f, SIZE_Y = 8f;
    private static final float STEP_X = -8f, STEP_Y = 0f;

    private static float[] uv(int frame) {
        float[] out = new float[4];
        ParticleComponentRuntimes.flipbookUv(out, BASE_U, BASE_V, SIZE_X, SIZE_Y, STEP_X, STEP_Y, frame, TEX, TEX);
        return out;
    }

    @Test
    void pixelUvIsNormalizedToUnitRange() {
        assertArrayEquals(new float[]{0.5f, 0.75f, 0.5625f, 0.8125f}, uv(0), 1e-6f);
    }

    @Test
    void everyFrameStaysInsideUnitRange() {
        for (int frame = 0; frame < 8; frame++) {
            float[] q = uv(frame);
            for (float value : q) {
                assertTrue(value >= 0f && value <= 1f, "帧 " + frame + " 的 UV 越界: " + value);
            }
            assertEquals(BASE_U / TEX + frame * (STEP_X / TEX), q[0], 1e-6f);
        }
        // 最后一帧贴到该行最左侧
        assertEquals(8f / TEX, uv(7)[0], 1e-6f);
    }

    @Test
    void zeroTextureSizeDoesNotProduceNonFiniteUv() {
        float[] out = new float[4];
        ParticleComponentRuntimes.flipbookUv(out, 1f, 2f, 3f, 4f, 0f, 0f, 0, 0f, -128f);
        for (float value : out) assertTrue(Float.isFinite(value), "纹理尺寸非法时 UV 必须是有限值");
        assertEquals(1f, out[0], 1e-6f);
        assertEquals(2f, out[1], 1e-6f);
    }

    @Test
    void stretchToLifetimeMapsProgressAcrossAllFrames() {
        // 寿命 4s：进度 0 → 帧 0；进度 7/8 → 帧 7（修复前恒为 0）
        assertEquals(0, ParticleComponentRuntimes.flipbookFrame(0f, 4f, 1f, 8, true, false));
        assertEquals(3, ParticleComponentRuntimes.flipbookFrame(1.5f, 4f, 1f, 8, true, false));
        assertEquals(7, ParticleComponentRuntimes.flipbookFrame(3.9f, 4f, 1f, 8, true, false));
        // 到寿命尽头也不越界
        assertEquals(7, ParticleComponentRuntimes.flipbookFrame(4f, 4f, 1f, 8, true, false));
        assertEquals(7, ParticleComponentRuntimes.flipbookFrame(9f, 4f, 1f, 8, true, false));
    }

    @Test
    void fpsPathAdvancesByAge() {
        assertEquals(0, ParticleComponentRuntimes.flipbookFrame(0.05f, 4f, 10f, 4, false, false));
        assertEquals(1, ParticleComponentRuntimes.flipbookFrame(0.15f, 4f, 10f, 4, false, false));
        // 非 loop：截断到最后一帧（age*fps = 50）
        assertEquals(3, ParticleComponentRuntimes.flipbookFrame(5f, 4f, 10f, 4, false, false));
        // loop：取模回卷（50 % 4 = 2）
        assertEquals(2, ParticleComponentRuntimes.flipbookFrame(5f, 4f, 10f, 4, false, true));
    }

    @Test
    void maxFrameZeroMeansNoFrameLimit() {
        // max_frame 缺省为 0：不取模/截断，按 fps 推进
        assertEquals(2, ParticleComponentRuntimes.flipbookFrame(0.25f, 4f, 10f, 0, false, false));
        // stretch 但没给 max_frame：映射不出帧号，停在第一帧（不退化成无界推进）
        assertEquals(0, ParticleComponentRuntimes.flipbookFrame(0.25f, 4f, 10f, 0, true, false));
        assertEquals(0, ParticleComponentRuntimes.flipbookFrame(3.75f, 4f, 10f, 0, true, false));
    }
}
