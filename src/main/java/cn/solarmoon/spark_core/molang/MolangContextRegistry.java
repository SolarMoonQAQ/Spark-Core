package cn.solarmoon.spark_core.molang;

import cn.solarmoon.spark_core.molang.runtime.MolangExpression;
import cn.solarmoon.spark_core.molang.runtime.MolangStringExpression;
import cn.solarmoon.spark_core.molang.runtime.Scope;
import cn.solarmoon.spark_core.molang.runtime.binding.JavaObjectBinding;
import cn.solarmoon.spark_core.molang.runtime.standard.MochaMath;
import cn.solarmoon.spark_core.molang.runtime.value.ObjectProperty;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Molang 引擎与编译表达式注册表。
 * <p>
 * 按上下文子类类型缓存 {@link MochaEngine} 和编译后的表达式，
 * 取代原先的全局单例 {@link SparkMolangEngine}。
 * <p>
 * 核心设计：
 * <ul>
 *   <li><b>引擎缓存</b>：每个 Context 子类一个 {@link MochaEngine}。
 *       引擎的 entity 为上下文原型实例，编译器扫描其 {@code @QueryBinding} 方法。</li>
 *   <li><b>表达式缓存</b>：按 (contextClass, expressionString) 去重，
 *       相同表达式跨实例复用编译结果。</li>
 *   <li><b>Scope 继承</b>：新引擎从全局 scope 复制所有命名空间绑定（math.* 等）。</li>
 * </ul>
 * <p>
 * 使用方式：
 * <pre>
 * // 编译（自动缓存）
 * MolangExpression expr = MolangContextRegistry.compile("subpart.durability", ctx);
 * // 求值（每次设置最新 animTime）
 * ctx.reset(animatable, animTime);
 * double val = expr.evaluate(ctx);
 * </pre>
 */
public final class MolangContextRegistry {

    /** 引擎缓存: 上下文子类 → MochaEngine */
    private static final ConcurrentMap<Class<?>, MochaEngine<?>> ENGINES = new ConcurrentHashMap<>();

    /** 表达式缓存键 — 按上下文类型和表达式字符串去重 */
    private record CacheKey(Class<?> contextClass, String expression) {}

    /** MolangExpression 缓存: (ctxClass, expr) → 编译后的表达式 */
    private static final ConcurrentMap<CacheKey, MolangExpression> EXPR_CACHE = new ConcurrentHashMap<>();

    /** MolangStringExpression 缓存 */
    private static final ConcurrentMap<CacheKey, MolangStringExpression> STR_EXPR_CACHE = new ConcurrentHashMap<>();

    /** 全局 scope 引用，用于复制绑定到新引擎 */
    private static volatile Scope globalScope;

    private MolangContextRegistry() {}

    /**
     * 初始化全局 scope（由 {@link SparkMolangEngine} 静态块调用）。
     * 必须在新引擎创建之前完成。
     */
    public static void initGlobalScope(Scope scope) {
        if (globalScope == null) {
            globalScope = scope;
        }
    }

    /**
     * 获取或创建指定上下文类对应的 {@link MochaEngine}。
     * <p>
     * 新引擎通过上下文子类的无参构造创建原型实例，
     * 编译器扫描原型实例类型上的 {@code @QueryBinding} 方法。
     * 同时从全局 scope 复制所有命名空间绑定（math.* 等）。
     *
     * @param contextClass 上下文子类（须继承 {@link SparkMolangContext} 且有无参构造）
     * @return 配置完毕的引擎实例
     */
    public static MochaEngine<?> getOrCreateEngine(Class<?> contextClass) {
        return ENGINES.computeIfAbsent(contextClass, clazz -> {
            // 通过无参构造创建上下文原型，供编译器扫描 @QueryBinding / @StringQueryBinding
            SparkMolangContext<?> proto;
            try {
                proto = (SparkMolangContext<?>) clazz.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException(
                        "无法创建上下文原型: " + clazz.getName() + "，请确保有无参构造函数", e);
            }
            return MochaEngine.create(proto, builder -> {
                // 从全局 scope 复制所有命名空间绑定（math.* 及第三方注册的 subpart.* 等）
                Scope current = globalScope;
                if (current != null) {
                    for (Map.Entry<String, ObjectProperty> entry : current.entries().entrySet()) {
                        ObjectProperty prop = entry.getValue();
                        if (prop != null && prop.value() != null) {
                            builder.set(entry.getKey(), prop.value());
                        }
                    }
                }
                // 确保 math.* 始终存在
                builder.set("math", JavaObjectBinding.of(MochaMath.class, null, new MochaMath()));
            });
        });
    }

    /**
     * 编译 MoLang 表达式为 {@link MolangExpression}。
     * 结果按 (contextClass, expression) 自动缓存。
     *
     * @param expr    MoLang 表达式字符串
     * @param context 当前求值上下文（用于确定引擎和缓存键）
     * @return 编译后的表达式
     */
    public static MolangExpression compile(String expr, SparkMolangContext<?> context) {
        CacheKey key = new CacheKey(context.getClass(), expr);
        return EXPR_CACHE.computeIfAbsent(key, k -> {
            MochaEngine<?> engine = getOrCreateEngine(k.contextClass());
            return engine.compile(k.expression(), MolangExpression.class);
        });
    }

    /**
     * 编译为 {@link MolangStringExpression}（支持字符串返回值）。
     * 结果按 (contextClass, expression) 自动缓存。
     */
    public static MolangStringExpression compileString(String expr, SparkMolangContext<?> context) {
        CacheKey key = new CacheKey(context.getClass(), expr);
        return STR_EXPR_CACHE.computeIfAbsent(key, k -> {
            MochaEngine<?> engine = getOrCreateEngine(k.contextClass());
            return engine.compile(k.expression(), MolangStringExpression.class);
        });
    }

    /**
     * 求值并返回多态结果（String / Double）。
     * 先走 StringExpression 通道，纯数值回退到 Double。
     *
     * @param expr    MoLang 表达式字符串
     * @param context 求值上下文
     * @return String 或 Double 结果
     */
    public static Object evalAsObject(String expr, SparkMolangContext<?> context) {
        String result = compileString(expr, context).evaluate(context);
        if (result != null) {
            try {
                return Double.parseDouble(result);
            } catch (NumberFormatException ignored) {
                return result;
            }
        }
        return compile(expr, context).evaluate(context);
    }
}
