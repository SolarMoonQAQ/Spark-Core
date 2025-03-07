package cn.solarmoon.spark_core.molang.core.value;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.engine.runtime.ExpressionEvaluator;
import com.mojang.serialization.Codec;
import net.minecraft.Util;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.List;

/**
 * 用于包装 {@link IValue} 对象的三维向量类型。
 * <p>
 * 内容既可以是 {@link DoubleValue} 也可以是 {@link MolangValue}。
 *
 * @param x
 * @param y
 * @param z
 */
public record Vector3k(
        IValue x,
        IValue y,
        IValue z
) {
    public static final Codec<Vector3k> CODEC = IValue.CODEC.listOf()
            .comapFlatMap(
                    list -> Util.fixedSize(list, 3).map(list3 -> new Vector3k(list3.getFirst(), list3.get(1), list3.get(2))),
                    vector3k -> List.of(vector3k.x(), vector3k.y(), vector3k.z())
            );

    public static final StreamCodec<FriendlyByteBuf, Vector3k> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public void encode(FriendlyByteBuf buf, @NotNull Vector3k vector3k) {
            buf.writeJsonWithCodec(CODEC, vector3k);
        }

        @Override
        public @NotNull Vector3k decode(FriendlyByteBuf buf) {
            return buf.readJsonWithCodec(CODEC);
        }
    };

    /**
     * 用给定的 {@link ExpressionEvaluator} 计算 {@link Vector3k} 表达式的值。
     *
     * @param evaluator 表达式求值器
     * @return 计算结果
     */
    public Vector3f eval(ExpressionEvaluator<?> evaluator) {
        return new Vector3f((float) x.evalAsDouble(evaluator),
                (float) y.evalAsDouble(evaluator),
                (float) z.evalAsDouble(evaluator));
    }

    public Vector3f eval(IAnimatable<?> animatable) {
        ExpressionEvaluator<?> evaluator = ExpressionEvaluator.evaluator(animatable);
        return new Vector3f((float) x.evalAsDouble(evaluator),
                (float) y.evalAsDouble(evaluator),
                (float) z.evalAsDouble(evaluator));
    }

}
