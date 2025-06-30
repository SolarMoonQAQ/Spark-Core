import { PinType, PinDirection } from './PinEnums.js';
import { PinDataType } from './DataTypes.js';

export class Pin {
    constructor(id, name, pinType, direction = null, dataType = null, defaultValue = null, isConnected = false, nodeId) {
        this.id = id;
        this.name = name;
        this.pinType = pinType;
        this.direction = direction;
        this.dataType = dataType;
        this.defaultValue = defaultValue;
        this.isConnected = isConnected;
        this.nodeId = nodeId;
    }
}
