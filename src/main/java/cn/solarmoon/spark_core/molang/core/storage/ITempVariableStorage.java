package cn.solarmoon.spark_core.molang.core.storage;

public interface ITempVariableStorage {
    Object getTemp(int address);

    void setTemp(int address, Object value);
}
