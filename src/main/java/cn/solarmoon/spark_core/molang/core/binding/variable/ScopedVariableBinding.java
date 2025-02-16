package cn.solarmoon.spark_core.molang.core.binding.variable;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.core.util.StringPool;
import cn.solarmoon.spark_core.molang.engine.runtime.AssignableVariable;
import cn.solarmoon.spark_core.molang.engine.runtime.ExecutionContext;
import cn.solarmoon.spark_core.molang.engine.runtime.binding.ObjectBinding;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("MapOrSetKeyShouldOverrideHashCodeEquals")
public class ScopedVariableBinding implements ObjectBinding {
    private final Int2ReferenceOpenHashMap<ScopedVariable> variableMap = new Int2ReferenceOpenHashMap<>();

    @Override
    public Object getProperty(String name) {
        return variableMap.computeIfAbsent(StringPool.computeIfAbsent(name), ScopedVariable::new);
    }

    public void reset() {
        variableMap.clear();
    }

    private record ScopedVariable(int name) implements AssignableVariable {
        @Override
        @SuppressWarnings("unchecked")
        public Object evaluate(final @NotNull ExecutionContext<?> context) {
            return ((IAnimatable<Object>) context.entity()).getScopedStorage().getScoped(name);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void assign(@NotNull ExecutionContext<?> context, Object value) {
            ((IAnimatable<Object>) context.entity()).getScopedStorage().setScoped(name, value);
        }
    }
}
