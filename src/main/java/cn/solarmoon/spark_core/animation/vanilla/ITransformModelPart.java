package cn.solarmoon.spark_core.animation.vanilla;

import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

import javax.annotation.Nullable;

public interface ITransformModelPart {

    @Nullable
    ModelPart getRoot();

    void setRoot(ModelPart root);

    Vector3f getPivot();

    void setPivot(Vector3f pivot);

}

