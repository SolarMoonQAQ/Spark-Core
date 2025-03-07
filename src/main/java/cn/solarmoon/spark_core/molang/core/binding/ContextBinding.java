package cn.solarmoon.spark_core.molang.core.binding;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.core.variable.IValueEvaluator;
import cn.solarmoon.spark_core.molang.core.variable.LambdaVariable;
import cn.solarmoon.spark_core.molang.core.variable.block.AbstractBlockVariable;
import cn.solarmoon.spark_core.molang.core.variable.block.BlockStateVariable;
import cn.solarmoon.spark_core.molang.core.variable.block.BlockVariable;
import cn.solarmoon.spark_core.molang.core.variable.entity.EntityVariable;
import cn.solarmoon.spark_core.molang.core.variable.entity.LivingEntityVariable;
import cn.solarmoon.spark_core.molang.core.variable.entity.MobEntityVariable;
import cn.solarmoon.spark_core.molang.core.variable.entity.TamableEntityVariable;
import cn.solarmoon.spark_core.molang.core.variable.item.ItemStackVariable;
import cn.solarmoon.spark_core.molang.core.variable.item.ItemVariable;
import cn.solarmoon.spark_core.molang.engine.runtime.Function;
import cn.solarmoon.spark_core.molang.engine.runtime.binding.ObjectBinding;
import cn.solarmoon.spark_core.skill.Skill;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class ContextBinding implements ObjectBinding {
    private final Object2ReferenceOpenHashMap<String, Object> bindings = new Object2ReferenceOpenHashMap<>();

    @Override
    public Object getProperty(String name) {
        return bindings.get(name);
    }

    public void function(String name, Function function) {
        bindings.put(name, function);
    }

    public void constValue(String name, Object value) {
        bindings.put(name, value);
    }

    public void var(String name, IValueEvaluator<?, IAnimatable<Object>> evaluator) {
        bindings.put(name, new LambdaVariable<>(evaluator));
    }

    public void entityVar(String name, IValueEvaluator<?, IAnimatable<Entity>> evaluator) {
        bindings.put(name, new EntityVariable(evaluator));
    }

    public void livingEntityVar(String name, IValueEvaluator<?, IAnimatable<LivingEntity>> evaluator) {
        bindings.put(name, new LivingEntityVariable(evaluator));
    }

    public void mobEntityVar(String name, IValueEvaluator<?, IAnimatable<Mob>> evaluator) {
        bindings.put(name, new MobEntityVariable(evaluator));
    }

    public void tamableEntityVar(String name, IValueEvaluator<?, IAnimatable<TamableAnimal>> evaluator) {
        bindings.put(name, new TamableEntityVariable(evaluator));
    }

    public void itemVar(String name, IValueEvaluator<?, IAnimatable<Item>> evaluator) {
        bindings.put(name, new ItemVariable(evaluator));
    }

    public void itemStackVar(String name, IValueEvaluator<?, IAnimatable<ItemStack>> evaluator) {
        bindings.put(name, new ItemStackVariable(evaluator));
    }

    public void blockStateVar(String name, IValueEvaluator<?, IAnimatable<BlockState>> evaluator) {
        bindings.put(name, new BlockStateVariable(evaluator));
    }

    public void blockVar(String name, IValueEvaluator<?, IAnimatable<Block>> evaluator) {
        bindings.put(name, new BlockVariable(evaluator));
    }

    public void abstractBlockVar(String name, IValueEvaluator<?, IAnimatable<BlockBehaviour>> evaluator) {
        bindings.put(name, new AbstractBlockVariable(evaluator));
    }
}
