// 原始来源: SimpleBedrockModel (https://github.com/McModderAnchor/SimpleBedrockModel)
// 许可证: MIT, Copyright (c) 2021-2025 Unnamed Team
// 已适配至 Spark-Core

package cn.solarmoon.spark_core.molang.runtime.binding;

import java.lang.annotation.*;

/**
 * 将 MolangContext 子类上的无参方法映射为 Molang 命名空间下的属性。
 * <p>
 * 用法：在 MolangContext 的子类中，对返回 {@code double} 的无参方法标注此注解。
 * <pre>
 * public class MyContext extends MolangContext&lt;MyEntity&gt; {
 *     // 基本用法：注册到默认命名空间 "query"
 *     &#64;QueryBinding("is_on_ground")
 *     public double isOnGround() { return getEntity().isOnGround() ? 1.0 : 0.0; }
 *
 *     // 映射到自定义命名空间，同时声明简写别名
 *     &#64;QueryBinding(value = "is_jumping", namespace = "input", aliases = {"in"})
 *     public double isJumping() { return ...; }
 * }
 * </pre>
 * 编译后 {@code query.is_on_ground}、{@code input.is_jumping}、
 * {@code in.is_jumping} 分别生成直接 INVOKEVIRTUAL 调用。
 * <p>
 * 此外，框架内置命名空间简写映射：{@code q → query}、{@code c → context}，
 * 无需在注解中重复声明。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface QueryBinding {
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
