package cn.solarmoon.spark_core.molang.core.storage;

public interface IScopedVariableStorage {
    Object getScoped(int name);

    void setScoped(int name, Object value);
}
