package cn.solarmoon.spark_core.molang;

import cn.solarmoon.spark_core.molang.runtime.MolangExpression;
import cn.solarmoon.spark_core.molang.runtime.MolangStringExpression;
import cn.solarmoon.spark_core.molang.runtime.binding.JavaObjectBinding;
import cn.solarmoon.spark_core.molang.runtime.standard.MochaMath;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 全局 MoLang scope 初始化器。
 * <p>
 * <b>已废弃</b>：编译和求值请使用 {@link MolangContextRegistry}。
 * 此类仅保留静态初始化块以构建全局 scope（math.* 等基础绑定），
 * 引擎管理和表达式编译均已迁移至 Registry。
 *
 * @deprecated 使用 {@link MolangContextRegistry#compile(String, SparkMolangContext)}
 */
@Deprecated
public final class SparkMolangEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger("Spark-Core/Molang");

    /**
     * 全局 scope 持有者（仅用于初始化 math.* 等基础绑定）。
     *
     * @deprecated 不应用于编译。使用 {@link MolangContextRegistry}。
     */
    @Deprecated
    public static final MochaEngine<?> ENGINE;

    static {
        SparkMolangContext<?> entityProto = new SparkMolangContext<>();
        ENGINE = MochaEngine.create(entityProto, builder -> {
            builder.set("math", JavaObjectBinding.of(MochaMath.class, null, new MochaMath()));

            AnimatableVariableBinding varBinding = new AnimatableVariableBinding(null);
            builder.set("variable", varBinding);
            builder.set("v", varBinding);
        });
        // 将全局 scope 注册到 Registry，使新引擎能继承所有绑定
        MolangContextRegistry.initGlobalScope(ENGINE.scope());
        LOGGER.info("MolangContextRegistry initialized with global scope");
    }

    private SparkMolangEngine() {}

    /** @deprecated 使用 {@link MolangContextRegistry#compile(String, SparkMolangContext)} */
    @Deprecated
    public static @NotNull MolangExpression compile(@NotNull String expr) {
        return ENGINE.compile(expr, MolangExpression.class);
    }

    /** @deprecated 使用 {@link MolangContextRegistry#compile(String, SparkMolangContext)} + evaluate */
    @Deprecated
    public static double eval(@NotNull String expr, @NotNull SparkMolangContext<?> context) {
        return compile(expr).evaluate(context);
    }

    /** @deprecated 使用 {@link MolangContextRegistry#compileString(String, SparkMolangContext)} + evaluate */
    @Deprecated
    public static String evalAsString(@NotNull String expr, @NotNull SparkMolangContext<?> context) {
        return ENGINE.compile(expr, MolangStringExpression.class).evaluate(context);
    }

    /** @deprecated 使用 {@link MolangContextRegistry#evalAsObject(String, SparkMolangContext)} */
    @Deprecated
    public static Object evalAsObject(@NotNull String expr, @NotNull SparkMolangContext<?> context) {
        return MolangContextRegistry.evalAsObject(expr, context);
    }
}
