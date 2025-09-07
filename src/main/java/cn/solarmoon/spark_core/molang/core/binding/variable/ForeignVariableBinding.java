package cn.solarmoon.spark_core.molang.core.binding.variable;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.core.storage.IForeignVariableStorage;
import cn.solarmoon.spark_core.molang.core.util.StringPool;
import cn.solarmoon.spark_core.molang.engine.runtime.ExecutionContext;
import cn.solarmoon.spark_core.molang.engine.runtime.Variable;
import cn.solarmoon.spark_core.molang.engine.runtime.binding.ObjectBinding;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("MapOrSetKeyShouldOverrideHashCodeEquals")
public class ForeignVariableBinding implements ObjectBinding {
    private final Int2ReferenceOpenHashMap<ForeignVariable> variableMap = new Int2ReferenceOpenHashMap<>();

    @Override
    public Object getProperty(String name) {
        return variableMap.computeIfAbsent(StringPool.computeIfAbsent(name), ForeignVariable::new);
    }

    public void reset() {
        variableMap.clear();
    }

    private record ForeignVariable(int name) implements Variable {
        @Override
        @SuppressWarnings("unchecked")
        public Object evaluate(final @NotNull ExecutionContext<?> context) {
            IForeignVariableStorage storage = ((IAnimatable<Object>) context.entity()).getAnimController().getForeignStorage();
            return storage.getPublic(name);
        }
    }
}
