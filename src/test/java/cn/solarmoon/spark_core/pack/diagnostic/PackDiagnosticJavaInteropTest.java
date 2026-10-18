package cn.solarmoon.spark_core.pack.diagnostic;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 编译期验证 Java 互操作：[PackContentException] 的构造器与 [PackCodec] 的默认参数重载。
 */
public class PackDiagnosticJavaInteropTest {

    @Test
    void constructsPackContentExceptionFromJava() {
        PackContentException exception = new PackContentException(Component.literal("bad content"));
        assertEquals("bad content", exception.getMessage());
    }

    @Test
    void readsCodecWithTwoArgumentOverload() {
        String value = PackCodec.read(Codec.STRING, "\"hello\"".getBytes(StandardCharsets.UTF_8));
        assertEquals("hello", value);
    }
}
