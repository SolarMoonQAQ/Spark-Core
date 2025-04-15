package cn.solarmoon.spark_core.mixin.js;

import cn.solarmoon.spark_core.js.extension.JSPhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import org.mozilla.javascript.Scriptable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Mixin(PhysicsCollisionObject.class)
public class PhysicsCollisionObjectMixin implements JSPhysicsCollisionObject {
    
    private final Map<String, List< Scriptable>> callBacks = new LinkedHashMap<>();
    
    @Override
    public Map<String, List< Scriptable>> getCallbackFunctions() {
        return callBacks;
    }
}
