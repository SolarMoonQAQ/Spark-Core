package cn.solarmoon.spark_core.molang.core.binding;

import cn.solarmoon.spark_core.molang.core.binding.variable.ForeignVariableBinding;
import cn.solarmoon.spark_core.molang.core.binding.variable.ScopedVariableBinding;
import cn.solarmoon.spark_core.molang.core.binding.variable.TempVariableBinding;
import cn.solarmoon.spark_core.molang.core.builtin.MathBinding;
import cn.solarmoon.spark_core.molang.core.builtin.QueryBinding;
import cn.solarmoon.spark_core.molang.engine.runtime.binding.ObjectBinding;
import cn.solarmoon.spark_core.molang.engine.runtime.binding.StandardBindings;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class PrimaryBinding implements ObjectBinding {
    protected final Object2ReferenceOpenHashMap<String, Object> bindings = new Object2ReferenceOpenHashMap<>();
    protected final ScopedVariableBinding scopedBinding = new ScopedVariableBinding();
    protected final ForeignVariableBinding foreignBinding = new ForeignVariableBinding();
    protected final TempVariableBinding tempBinding = new TempVariableBinding();

    public PrimaryBinding(@Nullable Map<String, ObjectBinding> extraBindings) {
        if (extraBindings != null) {
            bindings.putAll(extraBindings);
        }
        bindings.put("math", MathBinding.INSTANCE);
        bindings.put("query", QueryBinding.INSTANCE);
        bindings.put("q", QueryBinding.INSTANCE);
        bindings.put("loop", StandardBindings.LOOP_FUNC);
        bindings.put("for_each", StandardBindings.FOR_EACH_FUNC);

        bindings.put("variable", scopedBinding);
        bindings.put("v", scopedBinding);

        bindings.put("context", foreignBinding);
        bindings.put("c", foreignBinding);

        bindings.put("temp", tempBinding);
        bindings.put("t", tempBinding);
    }

    @Override
    public Object getProperty(String name) {
        return bindings.get(name);
    }

    public void reset() {
        scopedBinding.reset();
        foreignBinding.reset();
        tempBinding.reset();
    }

    public void popStackFrame() {
        tempBinding.reset();
    }
}
