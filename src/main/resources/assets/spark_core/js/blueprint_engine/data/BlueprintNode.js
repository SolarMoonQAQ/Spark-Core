import { Pin } from './BlueprintPin.js';

/**
 * BlueprintNode 数据结构
 * @property id 唯一标识
 * @property type 节点类型
 * @property position 位置坐标 { x, y }
 * @property inputs 输入引脚列表
 * @property outputs 输出引脚列表
 * @property properties 节点属性映射
 */
export class BlueprintNode {
    constructor(id, type, position = { x: 0, y: 0 }, inputs = [], outputs = [], properties = {}) {
        this.id = id;
        this.type = type;
        this.position = position;
        this.inputs = inputs;
        this.outputs = outputs;
        this.properties = properties;
    }
}
