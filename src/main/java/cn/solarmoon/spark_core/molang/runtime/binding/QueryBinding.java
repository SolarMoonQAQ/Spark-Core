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
 *     &#64;QueryBinding("is_on_ground")
 *     public double isOnGround() { return getEntity().isOnGround() ? 1.0 : 0.0; }
 *
 *     // 映射到自定义命名空间
 *     &#64;QueryBinding(value = "is_jumping", namespace = "input")
 *     public double isJumping() { return ...; }
 * }
 * </pre>
 * 编译后 {@code query.is_on_ground} 和 {@code input.is_jumping}
 * 分别生成直接 INVOKEVIRTUAL 调用。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface QueryBinding {
    /** 属性名 */
    String value();

    /** 命名空间，默认 {@code "query"} */
    String namespace() default "query";
}
