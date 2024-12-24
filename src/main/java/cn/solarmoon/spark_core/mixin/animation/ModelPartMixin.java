package cn.solarmoon.spark_core.mixin.animation;

import cn.solarmoon.spark_core.animation.vanilla.ITransformModelPart;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.ArrayList;

@Mixin(ModelPart.class)
public abstract class ModelPartMixin implements ITransformModelPart {

    private final Vector3f pivot = new Vector3f();
    private ModelPart root = null;

    @Inject(method = "translateAndRotate", at = @At("HEAD"))
    private void transHead(PoseStack poseStack, CallbackInfo ci) {
        poseStack.translate(pivot.x, pivot.y, pivot.z);
        var r = root;
        var list = new ArrayList<ModelPart>();
        while (r != null) {
            list.add(r);
            r = ((ITransformModelPart)r).getRoot();
        }
        for (int i = list.size() - 1; i >= 0; i--) {
            list.get(i).translateAndRotate(poseStack);
        }
    }

    @Inject(method = "translateAndRotate", at = @At("TAIL"))
    private void transTail(PoseStack poseStack, CallbackInfo ci) {
        poseStack.translate(-pivot.x, -pivot.y, -pivot.z);
    }

    @Override
    public Vector3f getPivot() {
        return pivot;
    }

    @Override
    public void setPivot(Vector3f pivot) {
        this.pivot.set(pivot);
    }

    @Override
    public @Nullable ModelPart getRoot() {
        return root;
    }

    @Override
    public void setRoot(ModelPart root) {
        this.root = root;
    }

}
