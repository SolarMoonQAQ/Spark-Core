package cn.solarmoon.spark_core.particle.client;

import cn.solarmoon.spark_core.molang.runtime.MolangExpression;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 基岩版粒子效果 JSON 反序列化器。
 * 将 particle_effect JSON 解析为 ParticleEffectDefinition。
 */
public class ParticleEffectDeserializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParticleEffectDeserializer.class);
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
                IComponentDefinition comp = ParticleComponentRegistry.deserialize(key, entry.getValue());
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

        // 只有「在世界坐标中仿真」的粒子才参与方块碰撞（§4.1）：
        // 局部空间粒子在实体坐标系里仿真、脚本粒子（parametric）的位置相对发射器，二者都没有可碰撞的地形。
        boolean hasParametric = false;
        for (IComponentDefinition def : particleComponents) {
            if (def instanceof ParticleMotionParametric) { hasParametric = true; break; }
        }
        boolean worldSpace = !emitterPreset.hasLocalPosition() && !hasParametric;
        String collisionGateReason = null;
        if (emitterPreset.hasLocalPosition() && hasParametric) {
            collisionGateReason = "它同时启用了 emitter_local_space.position 与 particle_motion_parametric";
        } else if (emitterPreset.hasLocalPosition()) {
            collisionGateReason = "它启用了 emitter_local_space.position（粒子在实体坐标系中仿真）";
        } else if (hasParametric) {
            collisionGateReason = "它使用了 particle_motion_parametric（位置相对发射器）";
        }
        ParticlePreset particlePreset = buildParticlePreset(particleComponents, molang, worldSpace, collisionGateReason, id);

        // 解析 curves
        Map<String, ParticleCurve> curves = parseCurves(effectObj.getAsJsonObject("curves"), molang);

        // 解析 events（传入 molang 以预编译表达式）
        Map<String, List<EventNode>> events = parseEvents(effectObj.getAsJsonObject("events"), molang);

        return new ParticleEffectDefinition(id, description, emitterPreset, particlePreset, curves, events);
    }

    /** 默认贴图（原版粒子图集精灵；等价于原版的 {@code minecraft:particle/particles}） */
    private static final String DEFAULT_TEXTURE = "textures/particle/particles";

    /** 贴图路径省略的默认命名空间 */
    private static final String DEFAULT_TEXTURE_NAMESPACE = "minecraft";

    /** 资源命名空间允许的字符（与 {@code ResourceLocation} 一致：不能含 '/'、':' 等） */
    private static final Pattern VALID_NAMESPACE = Pattern.compile("[a-z0-9_.-]+");

    /** 资源路径允许的字符（与 {@code ResourceLocation} 一致） */
    private static final Pattern VALID_PATH = Pattern.compile("[a-z0-9/._-]+");

    private static ParticleDescription parseDescription(JsonObject effectObj, ResourceLocation defaultId) {
        JsonObject desc = effectObj.getAsJsonObject("description");
        if (desc == null) {
            return new ParticleDescription(defaultId, "particles_blend", parseTexture(DEFAULT_TEXTURE));
        }

        String identifier = GSON.fromJson(desc.get("identifier"), String.class);
        ResourceLocation id = identifier != null ? parseLocation(identifier, defaultId, "identifier") : defaultId;

        JsonObject renderParams = desc.getAsJsonObject("basic_render_parameters");
        String material = "particles_blend";
        ResourceLocation texture = parseTexture(DEFAULT_TEXTURE);

        if (renderParams != null) {
            if (renderParams.has("material")) {
                material = renderParams.get("material").getAsString();
            }
            if (renderParams.has("texture")) {
                texture = parseTexture(renderParams.get("texture").getAsString());
            }
        }

        return new ParticleDescription(id, material, texture);
    }

    /**
     * 解析 JSON 里的贴图字段为资源位置。
     * <p>
     * 接受两种写法，且**先切分命名空间、再补全路径**（顺序不能反）：
     * <ul>
     *   <li>带命名空间引用：{@code "machine_max:textures/particle/explosion_atlas"}
     *       —— 命名空间是冒号前的部分，路径可能带也可能不带 {@code textures/} 前缀与 {@code .png} 后缀；</li>
     *   <li>省略命名空间：{@code "textures/particle/particles"}、{@code "particle/smoke"}
     *       —— 落到 {@code minecraft}；</li>
     * </ul>
     * 冒号前不是合法命名空间（例如写了绝对路径 {@code textures/machine_max:...}）、或补全后的路径含非法字符时，
     * 退回默认贴图并告警，而不是让整个粒子定义加载失败。
     * <p>
     * 这里的资源位置必须与 {@code TextureModule} 注册内容包贴图的方式一致：内容包里的
     * {@code <pack>/<namespace>/particle/explosion_atlas.png} 被注册为
     * {@code <namespace>:textures/particle/explosion_atlas.png}，因此 {@code .png} 后缀要带上
     * （{@code TextureModule} 用的是含后缀的完整文件名）。
     */
    private static ResourceLocation parseTexture(String raw) {
        String value = raw.trim();
        String namespace = DEFAULT_TEXTURE_NAMESPACE;

        int colon = value.indexOf(':');
        if (colon > 0) {
            String declared = value.substring(0, colon);
            if (VALID_NAMESPACE.matcher(declared).matches()) {
                namespace = declared;
                value = value.substring(colon + 1);
            } else {
                LOGGER.warn("[particle] 贴图 '{}' 的冒号前缀 '{}' 不是合法命名空间，已回退默认贴图 {}",
                        raw, declared, DEFAULT_TEXTURE);
                return parseTexture(DEFAULT_TEXTURE);
            }
        }

        if (!value.startsWith("textures/")) {
            value = "textures/" + value;
        }
        if (!value.endsWith(".png")) {
            value = value + ".png";
        }

        // 补全后仍需是合法资源位置，否则退回默认贴图（保持「贴图坏了不影响粒子效果本身」）
        if (!VALID_PATH.matcher(value).matches()) {
            LOGGER.warn("[particle] 贴图 '{}' 补全为 '{}:{}' 后不是合法资源位置，已回退默认贴图 {}",
                    raw, namespace, value, DEFAULT_TEXTURE);
            return parseTexture(DEFAULT_TEXTURE);
        }
        return ResourceLocation.fromNamespaceAndPath(namespace, value);
    }

    /**
     * 解析其它资源位置字段（如 {@code identifier}），非法时回退默认值并告警。
     */
    private static ResourceLocation parseLocation(String raw, ResourceLocation fallback, String field) {
        try {
            return ResourceLocation.parse(raw.trim());
        } catch (Exception e) {
            LOGGER.warn("[particle] {} '{}' 不是合法资源位置，已回退 {}: {}", field, raw, fallback, e.getMessage());
            return fallback;
        }
    }

    /**
     * 构建 EmitterPreset，同时预编译发射器引擎直接使用的 Molang 表达式。
     * 这些表达式存储在 EmitterPreset 中，所有发射器实例共享。
     */
    private static EmitterPreset buildEmitterPreset(List<IComponentDefinition> emitterDefs,
                                                     ParticleMolangEnvironment molang) {
        List<IEmitterComponent> runtimeComponents = new ArrayList<>();
        boolean hasLocalPos = false, hasLocalRot = false, hasLocalVel = false;

        // 预编译的表达式（反序列化时一次性编译，所有实例共享）
        MolangExpression spawnRateExpr = null;
        MolangExpression maxParticlesExpr = null;
        MolangExpression expirationExpr = null;
        MolangExpression activeTimeExpr = null;
        MolangExpression numParticlesExpr = null;

        for (IComponentDefinition def : emitterDefs) {
            if (def instanceof EmitterRateInstant rd) {
                runtimeComponents.add(ParticleComponentRuntimes.createRateInstant(rd, molang));
                numParticlesExpr = compileIfNotEmpty(rd.getNumParticles(), molang);
            } else if (def instanceof EmitterRateSteady rd) {
                runtimeComponents.add(ParticleComponentRuntimes.createRateSteady(rd, molang));
                spawnRateExpr = compileIfNotEmpty(rd.getSpawnRate(), molang);
                maxParticlesExpr = compileIfNotEmpty(rd.getMaxParticles(), molang);
            } else if (def instanceof EmitterRateManual rd) {
                runtimeComponents.add(ParticleComponentRuntimes.createRateManual(rd, molang));
                maxParticlesExpr = compileIfNotEmpty(rd.getMaxParticles(), molang);
            } else if (def instanceof EmitterLifetimeOnce ld) {
                runtimeComponents.add(ParticleComponentRuntimes.createLifetimeOnce(ld, molang));
                activeTimeExpr = compileIfNotEmpty(ld.getActiveTime(), molang);
            } else if (def instanceof EmitterLifetimeLooping ld) {
                runtimeComponents.add(ParticleComponentRuntimes.createLifetimeLooping(ld, molang));
            } else if (def instanceof EmitterLifetimeExpression ld) {
                runtimeComponents.add(ParticleComponentRuntimes.createLifetimeExpression(ld, molang));
                if (ld.getExpirationExpression() != null && !ld.getExpirationExpression().isEmpty()) {
                    expirationExpr = molang.compile(ld.getExpirationExpression());
                }
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

        return new EmitterPreset(runtimeComponents, emitterDefs,
                hasLocalPos, hasLocalRot, hasLocalVel,
                spawnRateExpr, maxParticlesExpr, expirationExpr,
                activeTimeExpr, numParticlesExpr);
    }

    /** 编译非空表达式，空或 null 返回 null */
    @Nullable
    private static MolangExpression compileIfNotEmpty(@Nullable String expr, ParticleMolangEnvironment molang) {
        if (expr == null || expr.isEmpty()) return null;
        return molang.compile(expr);
    }

    private static ParticlePreset buildParticlePreset(List<IComponentDefinition> particleDefs,
                                                       ParticleMolangEnvironment molang,
                                                       boolean worldSpace,
                                                       @Nullable String collisionGateReason,
                                                       ResourceLocation effectId) {
        List<IParticleComponent> runtimeComponents = new ArrayList<>();
        ParticlePreset.BillboardMode faceCameraMode = ParticlePreset.BillboardMode.ROTATE_XYZ;
        boolean hasLighting = false;
        List<ParticleMotionCollision.CollisionEvent> collisionEvents = List.of();

        for (IComponentDefinition def : particleDefs) {
            // 使用 ParticleComponentRuntimes 工厂创建运行时组件
            if (def instanceof ParticleMotionDynamic md) {
                runtimeComponents.add(ParticleComponentRuntimes.createMotionDynamic(md, molang));
            } else if (def instanceof ParticleMotionParametric md) {
                runtimeComponents.add(ParticleComponentRuntimes.createMotionParametric(md, molang));
            } else if (def instanceof ParticleMotionCollision md) {
                collisionEvents = md.getEvents();
                if (md.isEventsFieldPresent() && collisionEvents.isEmpty()) {
                    LOGGER.warn("粒子效果 {} 的 particle_motion_collision.events 存在但未解析出任何事件"
                            + "（应为事件对象或事件对象数组），该字段被忽略", effectId);
                }
                if (worldSpace) {
                    runtimeComponents.add(ParticleComponentRuntimes.createMotionCollision(md, molang));
                } else {
                    // 构建期门控：连每 tick 的 MoLang 求值都省掉（§4.6）
                    LOGGER.warn("粒子效果 {} 声明了 particle_motion_collision，但{}，方块碰撞被忽略"
                            + "（需要与地形交互时请改用世界空间发射器 + bindToAnchor）", effectId, collisionGateReason);
                }
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

        return new ParticlePreset(runtimeComponents, particleDefs, faceCameraMode, hasLighting, collisionEvents);
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

    private static Map<String, ParticleCurve> parseCurves(@Nullable JsonObject curvesObj,
                                                           ParticleMolangEnvironment molang) {
        Map<String, ParticleCurve> curves = new HashMap<>();
        if (curvesObj == null) return curves;

        // 使用 molang 环境的方法引用作为编译器
        ParticleCurve.MolangCompiler compiler = molang::compile;

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

            // 构造时预编译 Molang 表达式
            curves.put(varName, new ParticleCurve(curveType, input != null ? input : "",
                    horizontalRange, nodes, compiler));
        }

        return curves;
    }

    private static Map<String, List<EventNode>> parseEvents(@Nullable JsonObject eventsObj, ParticleMolangEnvironment molang) {
        Map<String, List<EventNode>> events = new HashMap<>();
        if (eventsObj == null) return events;

        for (Map.Entry<String, JsonElement> entry : eventsObj.entrySet()) {
            String key = entry.getKey();
            List<EventNode> nodes = parseEventList(entry.getValue(), molang);
            events.put(key, nodes);
        }

        return events;
    }

    private static List<EventNode> parseEventList(JsonElement element, ParticleMolangEnvironment molang) {
        List<EventNode> result = new ArrayList<>();
        if (element == null) return result;

        if (element.isJsonArray()) {
            for (JsonElement e : element.getAsJsonArray()) {
                EventNode node = parseSingleEvent(e, molang);
                if (node != null) result.add(node);
            }
        } else if (element.isJsonObject()) {
            EventNode node = parseSingleEvent(element, molang);
            if (node != null) result.add(node);
        }

        return result;
    }

    @Nullable
    private static EventNode parseSingleEvent(JsonElement element, ParticleMolangEnvironment molang) {
        if (!element.isJsonObject()) return null;
        JsonObject obj = element.getAsJsonObject();

        if (obj.has("particle_effect")) {
            JsonObject pe = obj.getAsJsonObject("particle_effect");
            String effect = GSON.fromJson(pe.get("effect"), String.class);
            EventNode.ParticleEffectType type = EventNode.ParticleEffectType.EMITTER;
            if (pe.has("type")) {
                type = EventNode.ParticleEffectType.fromString(pe.get("type").getAsString());
            }
            String preEffectExpr = GSON.fromJson(pe.get("pre_effect_expression"), String.class);
            // 预编译前置表达式
            var compiledPreEffect = (preEffectExpr != null && !preEffectExpr.isEmpty())
                    ? molang.compile(preEffectExpr) : null;
            return new EventNode.ParticleEffectEvent(effect, type, preEffectExpr, compiledPreEffect);
        }

        if (obj.has("sound_effect")) {
            JsonObject se = obj.getAsJsonObject("sound_effect");
            String sound = GSON.fromJson(se.get("sound"), String.class);
            return new EventNode.SoundEffectEvent(sound);
        }

        if (obj.has("sequence")) {
            JsonArray seqArr = obj.getAsJsonArray("sequence");
            List<EventNode> seq = parseEventList(seqArr, molang);
            return new EventNode.SequenceEvent(seq);
        }

        if (obj.has("randomize")) {
            JsonArray randArr = obj.getAsJsonArray("randomize");
            List<EventNode> randEvents = new ArrayList<>();
            List<Float> weights = new ArrayList<>();
            for (JsonElement re : randArr) {
                if (re.isJsonObject()) {
                    JsonObject reObj = re.getAsJsonObject();
                    EventNode node = parseSingleEvent(reObj, molang);
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
            // 预编译表达式事件
            var compiled = (expr != null && !expr.isEmpty()) ? molang.compile(expr) : null;
            return new EventNode.ExpressionEvent(expr, compiled);
        }

        if (obj.has("log")) {
            String message = GSON.fromJson(obj.get("log"), String.class);
            return new EventNode.LogEvent(message);
        }

        return null;
    }
}
