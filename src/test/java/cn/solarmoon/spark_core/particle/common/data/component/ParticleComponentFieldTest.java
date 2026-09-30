package cn.solarmoon.spark_core.particle.common.data.component;

import cn.solarmoon.spark_core.particle.common.data.component.appearance.ParticleAppearanceBillboard;
import cn.solarmoon.spark_core.particle.common.data.component.shape.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 组件字段名 / 字段格式的回归测试。
 * <p>
 * 起因：内容包写的字段名与反序列化读的字段名不一致，或用的格式（字符串 vs 数组）与读取方式不一致时，
 * 后果分两档——**静默失效**（字段被忽略，如 `facing_camera_mode`）或**整个定义加载失败**
 * （抛异常被内容包模块吞掉，游戏内表现为该效果整体缺失，如 `direction: "outwards"`）。
 */
class ParticleComponentFieldTest {

    private static JsonObject json(String raw) {
        return JsonParser.parseString(raw).getAsJsonObject();
    }

    // ==================== facing_camera_mode ====================

    @Test
    void cameraModeReadsSpecKey() {
        assertEquals("lookat_y", ParticleAppearanceBillboard
                .fromJson(json("{\"facing_camera_mode\": \"lookat_y\"}")).getFaceCameraMode());
    }

    @Test
    void cameraModeKeepsLegacySpellingWorking() {
        assertEquals("lookat_direction", ParticleAppearanceBillboard
                .fromJson(json("{\"face_camera_mode\": \"lookat_direction\"}")).getFaceCameraMode());
    }

    @Test
    void cameraModeDefaultsWhenAbsent() {
        assertEquals("rotate_xyz", ParticleAppearanceBillboard.fromJson(json("{}")).getFaceCameraMode());
    }

    // ==================== direction 的字符串形式 ====================

    @Test
    void stringDirectionIsAcceptedByEveryShape() {
        // 旧实现用 getAsJsonArray() 读取 direction，字符串值直接抛 ClassCastException
        assertDoesNotThrow(() -> EmitterShapePoint.fromJson(json("{\"direction\": \"outwards\"}")));
        assertDoesNotThrow(() -> EmitterShapeBox.fromJson(json("{\"direction\": \"outwards\"}")));
        assertDoesNotThrow(() -> EmitterShapeSphere.fromJson(json("{\"direction\": \"outwards\"}")));
        assertDoesNotThrow(() -> EmitterShapeDisc.fromJson(json("{\"direction\": \"outwards\"}")));
    }

    @Test
    void radialDirectionMapsToInwardsFlag() {
        assertFalse(EmitterShapeDisc.fromJson(json("{\"direction\": \"outwards\"}")).isDirectionInwards());
        assertTrue(EmitterShapeDisc.fromJson(json("{\"direction\": \"inwards\"}")).isDirectionInwards());
        assertTrue(EmitterShapeSphere.fromJson(json("{\"direction\": \"INWARDS\"}")).isDirectionInwards());
    }

    @Test
    void legacyDirectionInwardsFieldStillWorks() {
        assertTrue(EmitterShapeDisc.fromJson(json("{\"direction_inwards\": true}")).isDirectionInwards());
        assertFalse(EmitterShapeSphere.fromJson(json("{}")).isDirectionInwards());
    }

    @Test
    void vectorDirectionIsStillParsedVerbatim() {
        EmitterShapePoint point = EmitterShapePoint.fromJson(json("{\"direction\": [\"1\", \"-2\", \"3\"]}"));
        assertArrayEquals(new String[]{"1", "-2", "3"}, point.getDirection());
        // 字符串形式下径向分量由形状运行时给出，向量取零
        assertArrayEquals(new String[]{"0", "0", "0"},
                EmitterShapeSphere.fromJson(json("{\"direction\": \"outwards\"}")).getDirection());
    }

    // ==================== 非数组值不得让定义加载失败 ====================

    @Test
    void nonArrayVectorFieldsFallBackToDefaults() {
        EmitterShapeDisc disc = EmitterShapeDisc.fromJson(json("""
                {"offset": 5, "plane_normal": "up", "radius": 2, "surface_only": true}"""));
        assertArrayEquals(new String[]{"0", "0", "0"}, disc.getOffset());
        assertArrayEquals(new String[]{"0", "0", "1"}, disc.getPlaneNormal());
        assertEquals("2", disc.getRadius());
        assertTrue(disc.isSurfaceOnly());

        EmitterShapeBox box = EmitterShapeBox.fromJson(json("{\"half_dimensions\": 3}"));
        assertArrayEquals(new String[]{"1", "1", "1"}, box.getHalfDimensions());
    }

    // ==================== UV / 翻页书 ====================

    /** 规范里 uv / uv_size 允许 MoLang 表达式；解析不出数值时回退默认值而不是让整个定义加载失败 */
    @Test
    void uvExpressionFallsBackInsteadOfFailing() {
        ParticleAppearanceBillboard def = ParticleAppearanceBillboard.fromJson(json("""
                {"uv": {"texture_width": 128, "texture_height": 128,
                        "uv": ["math.floor(variable.particle_random_2*8)*8", 72],
                        "uv_size": [8, 8]}}"""));
        var uv = def.getUv();
        assertNotNull(uv);
        // u 回退为 0（该行第一格精灵），v 照常解析
        assertEquals(0f, uv.getU0(), 1e-6f);
        assertEquals(72f / 128f, uv.getV0(), 1e-6f);
        assertEquals(8f / 128f, uv.getU1(), 1e-6f);
        assertEquals(80f / 128f, uv.getV1(), 1e-6f);
    }

    @Test
    void uvSizeExpressionFallsBackToDefaultSize() {
        ParticleAppearanceBillboard def = ParticleAppearanceBillboard.fromJson(json("""
                {"uv": {"texture_width": 128, "texture_height": 128,
                        "uv": [32, 88], "uv_size": ["variable.size", "variable.size"]}}"""));
        var uv = def.getUv();
        assertNotNull(uv);
        // 默认尺寸 1 像素
        assertEquals(32f / 128f, uv.getU0(), 1e-6f);
        assertEquals(88f / 128f, uv.getV0(), 1e-6f);
        assertEquals(33f / 128f, uv.getU1(), 1e-6f);
        assertEquals(89f / 128f, uv.getV1(), 1e-6f);
    }

    @Test
    void absentUvBlockYieldsNoUvAndNoFlipbook() {
        var noUv = ParticleAppearanceBillboard.fromJson(json("{}"));
        assertNull(noUv.getUv());
        assertNull(noUv.getFlipbook());
    }

    /** 非数组的 uv / uv_size 回退到默认值（偏移 0 像素、尺寸 1 像素），不抛异常 */
    @Test
    void malformedUvFallsBackToPixelDefaults() {
        var malformed = ParticleAppearanceBillboard.fromJson(json("""
                {"uv": {"texture_width": 128, "texture_height": 128, "uv": 7, "uv_size": "8"}}"""));
        var uv = malformed.getUv();
        assertNotNull(uv);
        assertEquals(0f, uv.getU0(), 1e-6f);
        assertEquals(0f, uv.getV0(), 1e-6f);
        assertEquals(1f / 128f, uv.getU1(), 1e-6f);
        assertEquals(1f / 128f, uv.getV1(), 1e-6f);
    }

    /** 翻页书字段：规范用大写 UV，早期拼写用小写，两者都必须被读取（magic.particle.json 用的是规范写法） */
    @Test
    void flipbookAcceptsSpecAndLegacyKeys() {
        var spec = ParticleAppearanceBillboard.fromJson(json("""
                {"uv": {"texture_width": 128, "texture_height": 128,
                        "flipbook": {"base_UV": [64, 96], "size_UV": [8, 8], "step_UV": [-8, 0],
                                     "max_frame": 8, "stretch_to_lifetime": true}}}""")).getFlipbook();
        assertNotNull(spec);
        assertEquals("64", spec.getBaseU());
        assertEquals("96", spec.getBaseV());
        assertEquals("8", spec.getSizeX());
        assertEquals("-8", spec.getStepX());
        assertEquals("8", spec.getMaxFrame());
        assertTrue(spec.isStretchToLifetime());
        assertEquals(128f, spec.getTextureWidth(), 1e-6f);
        assertEquals(128f, spec.getTextureHeight(), 1e-6f);

        var legacy = ParticleAppearanceBillboard.fromJson(json("""
                {"uv": {"texture_width": 64, "texture_height": 64,
                        "flipbook": {"base_uv": [8, 8], "size_uv": [4, 4], "step_uv": [4, 0],
                                     "fps": 10, "max_frame": 4, "loop": true}}}""")).getFlipbook();
        assertNotNull(legacy);
        assertEquals("8", legacy.getBaseU());
        assertEquals("4", legacy.getSizeX());
        assertEquals("10", legacy.getFps());
        assertTrue(legacy.isLoop());
    }

    @Test
    void malformedFlipbookFallsBackToDefaults() {
        var fb = ParticleAppearanceBillboard.fromJson(json("""
                {"uv": {"texture_width": 128, "texture_height": 128,
                        "flipbook": {"base_UV": 64, "size_UV": [8, 8], "step_UV": [0, 0]}}}""")).getFlipbook();
        assertNotNull(fb);
        assertEquals("0", fb.getBaseU());
        assertEquals("0", fb.getBaseV());
    }
}
