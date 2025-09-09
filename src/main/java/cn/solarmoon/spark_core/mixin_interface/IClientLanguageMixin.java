package cn.solarmoon.spark_core.mixin_interface;

import net.minecraft.network.chat.Component;

import java.util.Map;

public interface IClientLanguageMixin {
    void spark_core$addExtraStorage(Map<String, String> extra);

    void spark_core$addExtraComponentStorage(Map<String, Component> extra);
}
