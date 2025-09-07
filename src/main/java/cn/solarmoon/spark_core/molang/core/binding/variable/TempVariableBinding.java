package cn.solarmoon.spark_core.molang.core.binding.variable;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.engine.runtime.AssignableVariable;
import cn.solarmoon.spark_core.molang.engine.runtime.ExecutionContext;
import cn.solarmoon.spark_core.molang.engine.runtime.binding.ObjectBinding;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import org.jetbrains.annotations.NotNull;

public class TempVariableBinding implements ObjectBinding {
    private final Object2ReferenceMap<String, TempVariable> variableMap = new Object2ReferenceOpenHashMap<>();
    private int topPointer = 0;

    @Override
    public Object getProperty(String name) {
        return variableMap.computeIfAbsent(name, k -> new TempVariable(topPointer++));
    }

    public void reset() {
        variableMap.clear();
        topPointer = 0;
    }

    private record TempVariable(int address) implements AssignableVariable {
        @Override
        @SuppressWarnings("unchecked")
        public Object evaluate(final @NotNull ExecutionContext<?> context) {
            return ((IAnimatable<Object>) context.entity()).getAnimController().getTempStorage().getTemp(address);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void assign(@NotNull ExecutionContext<?> context, Object value) {
            ((IAnimatable<Object>) context.entity()).getAnimController().getTempStorage().setTemp(address, value);
        }
    }
}
