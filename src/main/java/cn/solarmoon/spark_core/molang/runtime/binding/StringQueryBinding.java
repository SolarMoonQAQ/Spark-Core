// 原始来源: SimpleBedrockModel (https://github.com/McModderAnchor/SimpleBedrockModel)
// 许可证: MIT, Copyright (c) 2021-2025 Unnamed Team
// 已适配至 Spark-Core

package cn.solarmoon.spark_core.molang.runtime.binding;

import java.lang.annotation.*;

/**
 * 将 MolangContext 子类上返回 {@code String} 的方法映射为 MoLang 命名空间属性。
 * 与 {@link QueryBinding} 对称，但方法返回 {@code String} 而非 double。
 * <p>
 * {@code null} 返回值表示空值，可用于 {@code ??} 空值合并兜底。
 * <pre>
 * public class MyContext extends MolangContext&lt;MyEntity&gt; {
 *     &#64;StringQueryBinding(value = "get", namespace = "subpart", aliases = {"spt"})
 *     public String subpartGetString(String channel) {
 *         Object val = getEntity().getAnimatable().variables.get(channel);
 *         return val != null ? val.toString() : null; // null → ?? 兜底
 *     }
 * }
 * </pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface StringQueryBinding {
    /** 属性名 */
    String value();

    /** 命名空间，默认 {@code "query"} */
    String namespace() default "query";

    /**
     * 命名空间简写别名。
     * 例如设为 {@code {"spt"}} 后，该属性同时可通过 {@code spt.xxx} 访问。
     */
    String[] aliases() default {};
}
