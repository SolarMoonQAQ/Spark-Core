package cn.solarmoon.spark_core.mixin.js;

import cn.solarmoon.spark_core.js2.extension.JSPhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import org.graalvm.polyglot.Value;
import org.spongepowered.asm.mixin.Mixin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Mixin(PhysicsCollisionObject.class)
public class PhysicsCollisionObjectMixin implements JSPhysicsCollisionObject {
    
    private final Map<String, List< Value>> callBacks = new LinkedHashMap<>();
    
    @Override
    public Map<String, List<Value>> getCallbackFunctions() {
        return callBacks;
    }
}
