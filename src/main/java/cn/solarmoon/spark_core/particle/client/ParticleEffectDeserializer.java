package cn.solarmoon.spark_core.particle.client;

import cn.solarmoon.spark_core.particle.common.data.*;
import cn.solarmoon.spark_core.particle.common.data.curve.ParticleCurve;
import cn.solarmoon.spark_core.particle.common.data.event.EventNode;
import cn.solarmoon.spark_core.particle.common.data.IComponentDefinition;
import cn.solarmoon.spark_core.particle.common.data.component.EmitterLocalSpace;
import cn.solarmoon.spark_core.particle.common.data.component.appearance.ParticleAppearanceBillboard;
import cn.solarmoon.spark_core.particle.common.data.component.appearance.ParticleAppearanceLighting;
import cn.solarmoon.spark_core.particle.common.data.component.rate.*;
import cn.solarmoon.spark_core.particle.common.data.component.lifetime.*;
import cn.solarmoon.spark_core.particle.common.data.component.shape.*;
import cn.solarmoon.spark_core.particle.common.data.component.motion.*;
import cn.solarmoon.spark_core.particle.common.data.component.init.*;
import cn.solarmoon.spark_core.particle.common.data.component.appearance.*;
import cn.solarmoon.spark_core.particle.common.data.component.expire.*;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 基岩版粒子效果 JSON 反序列化器。
 * 将 particle_effect JSON 解析为 ParticleEffectDefinition。
 */
public class ParticleEffectDeserializer {

    private static final Gson GSON = new Gson();

    /**
     * 反序列化完整的粒子效果 JSON。
     *
     * @param id   粒子标识符
     * @param root JSON 根元素
     * @return 解析后的 ParticleEffectDefinition
     */
    public static ParticleEffectDefinition deserialize(ResourceLocation id, JsonElement root) {
        return deserialize(id, root, null);
    }

    /**
     * 反序列化完整的粒子效果 JSON，并提供 Molang 环境以编译表达式。
     *
     * @param id         粒子标识符
     * @param root       JSON 根元素
     * @param molang     Molang 求值环境（可选，为 null 则不编译运行时组件）
     * @return 解析后的 ParticleEffectDefinition
     */
    public static ParticleEffectDefinition deserialize(ResourceLocation id, JsonElement root,
                                                        @Nullable ParticleMolangEnvironment molang) {
        if (root == null || !root.isJsonObject()) {
            throw new IllegalArgumentException("无效的粒子效果 JSON: 根元素不是对象");
        }

        JsonObject obj = root.getAsJsonObject();

        // 兼容有 particle_effect 外层包装的格式
        JsonObject effectObj = obj;
        if (obj.has("particle_effect") && obj.get("particle_effect").isJsonObject()) {
            effectObj = obj.getAsJsonObject("particle_effect");
        }

        // 解析 description
        ParticleDescription description = parseDescription(effectObj, id);

        // 解析 components
        List<IComponentDefinition> emitterComponents = new ArrayList<>();
        List<IComponentDefinition> particleComponents = new ArrayList<>();
        JsonObject componentsObj = effectObj.getAsJsonObject("components");
        if (componentsObj != null) {
            for (Map.Entry<String, JsonElement> entry : componentsObj.entrySet()) {
                String key = entry.getKey();
                if (!entry.getValue().isJsonObject()) continue;

                IComponentDefinition comp = ParticleComponentRegistry.deserialize(key, entry.getValue().getAsJsonObject());
                if (comp instanceof IEmitterComponentDefinition) {
                    emitterComponents.add(comp);
                } else if (comp instanceof IParticleComponentDefinition) {
                    particleComponents.add(comp);
                }
                // 未知组件静默忽略
            }
        }

        // 按 order() 排序
        emitterComponents.sort(Comparator.comparingInt(IComponentDefinition::order));
        particleComponents.sort(Comparator.comparingInt(IComponentDefinition::order));

        // 创建 Molang 环境（如果未提供）
        boolean ownMolang = false;
        if (molang == null) {
            molang = new ParticleMolangEnvironment();
            ownMolang = true;
        }

        // 构建 Preset（使用 ParticleComponentRuntimes 工厂）
        EmitterPreset emitterPreset = buildEmitterPreset(emitterComponents, molang);
        ParticlePreset particlePreset = buildParticlePreset(particleComponents, molang);

        // 解析 curves
        Map<String, ParticleCurve> curves = parseCurves(effectObj.getAsJsonObject("curves"));

        // 解析 events
        Map<String, List<EventNode>> events = parseEvents(effectObj.getAsJsonObject("events"));

        return new ParticleEffectDefinition(id, description, emitterPreset, particlePreset, curves, events);
    }

    private static ParticleDescription parseDescription(JsonObject effectObj, ResourceLocation defaultId) {
        JsonObject desc = effectObj.getAsJsonObject("description");
        if (desc == null) {
            return new ParticleDescription(defaultId, "particles_blend",
                    ResourceLocation.parse("textures/particle/particles.png"));
        }

        String identifier = GSON.fromJson(desc.get("identifier"), String.class);
        ResourceLocation id = identifier != null ? ResourceLocation.parse(identifier) : defaultId;

        JsonObject renderParams = desc.getAsJsonObject("basic_render_parameters");
        String material = "particles_blend";
        ResourceLocation texture = ResourceLocation.parse("textures/particle/particles.png");

        if (renderParams != null) {
            if (renderParams.has("material")) {
                material = renderParams.get("material").getAsString();
            }
            if (renderParams.has("texture")) {
                String texStr = renderParams.get("texture").getAsString();
                // 基岩版路径: "textures/particle/smoke" → Java版: "namespace:textures/particle/smoke.png"
                // 无冒号时 ResourceLocation.parse() 自动使用默认命名空间 minecraft:
                if (!texStr.startsWith("textures/")) {
                    texStr = "textures/" + texStr;
                }
                if (!texStr.endsWith(".png")) {
                    texStr = texStr + ".png";
                }
                texture = ResourceLocation.parse(texStr);
            }
        }

        return new ParticleDescription(id, material, texture);
    }

    private static EmitterPreset buildEmitterPreset(List<IComponentDefinition> emitterDefs,
                                                     ParticleMolangEnvironment molang) {
        List<IEmitterComponent> runtimeComponents = new ArrayList<>();
        boolean hasLocalPos = false, hasLocalRot = false, hasLocalVel = false;

        for (IComponentDefinition def : emitterDefs) {
            // 使用 ParticleComponentRuntimes 工厂创建运行时组件
            if (def instanceof EmitterRateInstant rd) {
                runtimeComponents.add(ParticleComponentRuntimes.createRateInstant(rd, molang));
            } else if (def instanceof EmitterRateSteady rd) {
                runtimeComponents.add(ParticleComponentRuntimes.createRateSteady(rd, molang));
            } else if (def instanceof EmitterRateManual rd) {
                runtimeComponents.add(ParticleComponentRuntimes.createRateManual(rd, molang));
            } else if (def instanceof EmitterLifetimeOnce ld) {
                runtimeComponents.add(ParticleComponentRuntimes.createLifetimeOnce(ld));
            } else if (def instanceof EmitterLifetimeLooping ld) {
                runtimeComponents.add(ParticleComponentRuntimes.createLifetimeLooping(ld));
            } else if (def instanceof EmitterLifetimeExpression ld) {
                runtimeComponents.add(ParticleComponentRuntimes.createLifetimeExpression(ld, molang));
            } else if (def instanceof EmitterInitialization ed) {
                runtimeComponents.add(ParticleComponentRuntimes.createEmitterInit(ed, molang));
            } else if (def instanceof EmitterLocalSpace els) {
                 runtimeComponents.add(ParticleComponentRuntimes.createLocalSpace(els));
                 hasLocalPos = els.isPosition();
                 hasLocalRot = els.isRotation();
                 hasLocalVel = els.isVelocity();
             } else if (def instanceof EmitterShapePoint sd) {
                 runtimeComponents.add(ParticleComponentRuntimes.createShapePoint(sd, molang));
             } else if (def instanceof EmitterShapeSphere sd) {
                 runtimeComponents.add(ParticleComponentRuntimes.createShapeSphere(sd, molang));
             } else if (def instanceof EmitterShapeBox sd) {
                 runtimeComponents.add(ParticleComponentRuntimes.createShapeBox(sd, molang));
             } else if (def instanceof EmitterShapeDisc sd) {
                 runtimeComponents.add(ParticleComponentRuntimes.createShapeDisc(sd, molang));
             } else if (def instanceof EmitterShapeEntityAABB sd) {
                 runtimeComponents.add(ParticleComponentRuntimes.createShapeEntityAABB(sd, molang));
             } else if (def instanceof IEmitterComponentDefinition ecd) {
                runtimeComponents.add(ecd.createRuntime());
            }
        }

        return new EmitterPreset(runtimeComponents, emitterDefs, hasLocalPos, hasLocalRot, hasLocalVel);
    }

    private static ParticlePreset buildParticlePreset(List<IComponentDefinition> particleDefs,
                                                       ParticleMolangEnvironment molang) {
        List<IParticleComponent> runtimeComponents = new ArrayList<>();
        ParticlePreset.BillboardMode faceCameraMode = ParticlePreset.BillboardMode.ROTATE_XYZ;
        boolean hasLighting = false;

        for (IComponentDefinition def : particleDefs) {
            // 使用 ParticleComponentRuntimes 工厂创建运行时组件
            if (def instanceof ParticleMotionDynamic md) {
                runtimeComponents.add(ParticleComponentRuntimes.createMotionDynamic(md, molang));
            } else if (def instanceof ParticleMotionParametric md) {
                runtimeComponents.add(ParticleComponentRuntimes.createMotionParametric(md, molang));
            } else if (def instanceof ParticleMotionCollision md) {
                runtimeComponents.add(ParticleComponentRuntimes.createMotionCollision(md, molang));
            } else if (def instanceof ParticleAppearanceBillboard bd) {
                runtimeComponents.add(ParticleComponentRuntimes.createAppearanceBillboard(bd, molang));
                faceCameraMode = parseBillboardMode(bd.getFaceCameraMode());
            } else if (def instanceof ParticleAppearanceTinting td) {
                runtimeComponents.add(ParticleComponentRuntimes.createAppearanceTinting(td, molang));
            } else if (def instanceof ParticleAppearanceLighting ld) {
                 hasLighting = true;
                 runtimeComponents.add(ParticleComponentRuntimes.createAppearanceLighting(ld));
             } else if (def instanceof ParticleInitialSpeed sd) {
                 runtimeComponents.add(ParticleComponentRuntimes.createInitialSpeed(sd, molang));
             } else if (def instanceof ParticleInitialSpin sd) {
                 runtimeComponents.add(ParticleComponentRuntimes.createInitialSpin(sd, molang));
             } else if (def instanceof ParticleInitialization id) {
                 runtimeComponents.add(ParticleComponentRuntimes.createParticleInit(id, molang));
             } else if (def instanceof ParticleLifetimeExpression ld) {
                 runtimeComponents.add(ParticleComponentRuntimes.createLifetimeExpression(ld, molang));
             } else if (def instanceof ParticleLifetimeKillPlane kd) {
                 runtimeComponents.add(ParticleComponentRuntimes.createKillPlane(kd));
             } else if (def instanceof ParticleExpireIfInBlocks ed) {
                 runtimeComponents.add(ParticleComponentRuntimes.createExpireIfInBlocks(ed, molang));
             } else if (def instanceof ParticleExpireIfNotInBlocks ed) {
                 runtimeComponents.add(ParticleComponentRuntimes.createExpireIfNotInBlocks(ed, molang));
             } else if (def instanceof IParticleComponentDefinition pcd) {
                runtimeComponents.add(pcd.createRuntime());
            }
        }

        return new ParticlePreset(runtimeComponents, faceCameraMode, hasLighting);
    }

    /**
     * 将基岩版公告板朝向模式字符串映射到枚举。
     */
    private static ParticlePreset.BillboardMode parseBillboardMode(String mode) {
        if (mode == null) return ParticlePreset.BillboardMode.ROTATE_XYZ;
        return switch (mode) {
            case "rotate_xyz" -> ParticlePreset.BillboardMode.ROTATE_XYZ;
            case "rotate_y" -> ParticlePreset.BillboardMode.ROTATE_Y;
            case "lookat_xyz" -> ParticlePreset.BillboardMode.LOOKAT_XYZ;
            case "lookat_y" -> ParticlePreset.BillboardMode.LOOKAT_Y;
            case "lookat_direction" -> ParticlePreset.BillboardMode.LOOKAT_DIRECTION;
            case "direction_x" -> ParticlePreset.BillboardMode.DIRECTION_X;
            case "direction_y" -> ParticlePreset.BillboardMode.DIRECTION_Y;
            case "direction_z" -> ParticlePreset.BillboardMode.DIRECTION_Z;
            case "emitter_transform_xy" -> ParticlePreset.BillboardMode.EMITTER_TRANSFORM_XY;
            case "emitter_transform_xz" -> ParticlePreset.BillboardMode.EMITTER_TRANSFORM_XZ;
            case "emitter_transform_yz" -> ParticlePreset.BillboardMode.EMITTER_TRANSFORM_YZ;
            default -> ParticlePreset.BillboardMode.ROTATE_XYZ;
        };
    }

    private static Map<String, ParticleCurve> parseCurves(@Nullable JsonObject curvesObj) {
        Map<String, ParticleCurve> curves = new HashMap<>();
        if (curvesObj == null) return curves;

        for (Map.Entry<String, JsonElement> entry : curvesObj.entrySet()) {
            String varName = entry.getKey(); // e.g. "variable.curve_size"
            JsonObject curveObj = entry.getValue().getAsJsonObject();

            String type = GSON.fromJson(curveObj.get("type"), String.class);
            String input = GSON.fromJson(curveObj.get("input"), String.class);
            // horizontal_range 可为数值或 MoLang 表达式（如 "variable.particle_lifetime"）
            String horizontalRange = GSON.fromJson(curveObj.get("horizontal_range"), String.class);
            if (horizontalRange == null) horizontalRange = "1";
            JsonArray nodesArr = curveObj.getAsJsonArray("nodes");

            List<Float> nodes = new ArrayList<>();
            if (nodesArr != null) {
                for (JsonElement n : nodesArr) {
                    nodes.add(n.getAsFloat());
                }
            }

            ParticleCurve.CurveType curveType = ParticleCurve.CurveType.LINEAR;
            if (type != null) {
                curveType = switch (type) {
                    case "bezier" -> ParticleCurve.CurveType.BEZIER;
                    case "catmull_rom" -> ParticleCurve.CurveType.CATMULL_ROM;
                    case "bezier_chain" -> ParticleCurve.CurveType.BEZIER_CHAIN;
                    default -> ParticleCurve.CurveType.LINEAR;
                };
            }

            curves.put(varName, new ParticleCurve(curveType, input != null ? input : "", horizontalRange, nodes));
        }

        return curves;
    }

    private static Map<String, List<EventNode>> parseEvents(@Nullable JsonObject eventsObj) {
        Map<String, List<EventNode>> events = new HashMap<>();
        if (eventsObj == null) return events;

        for (Map.Entry<String, JsonElement> entry : eventsObj.entrySet()) {
            String key = entry.getKey();
            List<EventNode> nodes = parseEventList(entry.getValue());
            events.put(key, nodes);
        }

        return events;
    }

    private static List<EventNode> parseEventList(JsonElement element) {
        List<EventNode> result = new ArrayList<>();
        if (element == null) return result;

        if (element.isJsonArray()) {
            for (JsonElement e : element.getAsJsonArray()) {
                EventNode node = parseSingleEvent(e);
                if (node != null) result.add(node);
            }
        } else if (element.isJsonObject()) {
            EventNode node = parseSingleEvent(element);
            if (node != null) result.add(node);
        }

        return result;
    }

    @Nullable
    private static EventNode parseSingleEvent(JsonElement element) {
        if (!element.isJsonObject()) return null;
        JsonObject obj = element.getAsJsonObject();

        if (obj.has("particle_effect")) {
            JsonObject pe = obj.getAsJsonObject("particle_effect");
            String effect = GSON.fromJson(pe.get("effect"), String.class);
            String preEffectExpr = GSON.fromJson(pe.get("pre_effect_expression"), String.class);
            return new EventNode.ParticleEffectEvent(effect, preEffectExpr);
        }

        if (obj.has("sound_effect")) {
            JsonObject se = obj.getAsJsonObject("sound_effect");
            String sound = GSON.fromJson(se.get("sound"), String.class);
            return new EventNode.SoundEffectEvent(sound);
        }

        if (obj.has("sequence")) {
            JsonArray seqArr = obj.getAsJsonArray("sequence");
            List<EventNode> seq = parseEventList(seqArr);
            return new EventNode.SequenceEvent(seq);
        }

        if (obj.has("randomize")) {
            JsonArray randArr = obj.getAsJsonArray("randomize");
            List<EventNode> randEvents = new ArrayList<>();
            List<Float> weights = new ArrayList<>();
            for (JsonElement re : randArr) {
                if (re.isJsonObject()) {
                    JsonObject reObj = re.getAsJsonObject();
                    EventNode node = parseSingleEvent(reObj);
                    if (node != null) {
                        randEvents.add(node);
                        float weight = GSON.fromJson(reObj.get("weight"), float.class);
                        weights.add(weight > 0 ? weight : 1);
                    }
                }
            }
            return new EventNode.RandomizeEvent(randEvents, weights);
        }

        if (obj.has("expression")) {
            String expr = GSON.fromJson(obj.get("expression"), String.class);
            return new EventNode.ExpressionEvent(expr);
        }

        if (obj.has("log")) {
            String message = GSON.fromJson(obj.get("log"), String.class);
            return new EventNode.LogEvent(message);
        }

        return null;
    }
}
