/**
 * Spark Core 物理碰撞功能示例
 *
 * 本示例展示如何在CustomNPCs的JavaScript脚本中使用SparkApi提供的物理碰撞功能。
 * 包括创建碰撞盒、注册碰撞回调和处理碰撞事件等功能。
 */
var SparkAPI = Java.type("com.jme3.npc_adapter.SparkCustomnpcApi");
var spark_api = new SparkAPI()

// 全局变量，用于存储碰撞盒ID
var weaponCollisionBoxId = null;
var shieldCollisionBoxId = null;
var is_init = false;

// 碰撞事件ID，用于在trigger函数中识别事件
var WEAPON_COLLISION_EVENT_ID = 1001;
var WEAPON_ATTACK_EVENT_ID = 1002;
var SHIELD_COLLISION_EVENT_ID = 1003;

/**
 * 初始化函数，在NPC加载时调用
 * 在这里创建碰撞盒并注册回调
 */
function interact(event) {
    // 记录初始化信息
    var npc = event.npc;
    var uuid = event.npc.getUUID();
    npc.say("初始化物理碰撞系统...");
    spark_api.changeModel(uuid, "minecraft:steve");
    try {
        // 需要延迟注册
        if (is_init) {
            npc.say("碰撞盒已注册");
            is_init = false;
        }else {
            // 为NPC的右手创建武器碰撞盒
            // 参数：modelId, boneName, sizeX, sizeY, sizeZ, offsetX, offsetY, offsetZ
            weaponCollisionBoxId = spark_api.createCollisionBoxBoundToBone(
                npc.getUUID(),  // 使用NPC的UUID作为模型ID
                "rightForeArm",    // 绑定到右手骨骼
                1, 1, 1,  // 碰撞盒尺寸（米）
                0.3, 0, 0,       // 相对于骨骼的偏移（米）
                WEAPON_COLLISION_EVENT_ID
            );

            if (weaponCollisionBoxId) {
                npc.say("武器碰撞盒创建成功: " + weaponCollisionBoxId);
            } else {
                npc.say("武器碰撞盒创建失败!");
            }

            // 为NPC的左手创建盾牌碰撞盒
            shieldCollisionBoxId = spark_api.createCollisionBoxBoundToBone(
                npc.getUUID(),  // 使用NPC的UUID作为模型ID
                "leftForeArm",     // 绑定到左手骨骼
                1, 1, 1,  // 碰撞盒尺寸（米）
                0.2, 0, 0,       // 相对于骨骼的偏移（米）
                WEAPON_COLLISION_EVENT_ID
            );
            if (shieldCollisionBoxId) {
                npc.say("盾牌碰撞盒创建成功: " + shieldCollisionBoxId);
            } else {
                npc.say("盾牌碰撞盒创建失败!");
            }
            is_init = true;
        }
    } catch (e) {
        npc.say("初始化物理碰撞系统时发生错误: " + e.message);
    }
}/**
 * 受伤函数，当NPC受伤时调用
 * 在这里可以添加受伤相关的逻辑
 */
function damaged(event) {
    event.setCanceled(true)
    spark_api.playAnimation(uuid, "steve", "hurt", 0.5);
}

/**
 * 触发器函数，处理自定义事件
 * 在这里处理碰撞事件
 */
function trigger(event) {
    var entity = event.entity;

    // 处理武器普通碰撞事件
    if (event.id === WEAPON_COLLISION_EVENT_ID) {

        var type = event.arguments[0];       // 碰撞类型: "start", "processed", "end"
        var Name1 = event.arguments[1];  // 另一个碰撞体的名称
        var Name2 = event.arguments[2];  // 另一个碰撞体的名称
        var manifoldId = event.arguments[3]; // 碰撞点ID
        if (type === "start") {
            entity.say("武器开始碰撞: " + otherName);
        } else if (type === "processed") {
            // 获取碰撞点位置
            print(Name1, Name2)
        } else if (type === "end") {
            entity.say("武器结束碰撞: " + otherName);
        }
    }
}
function init(event) {
    var npc = event.npc;
    var uuid = event.npc.getUUID();
    npc.say("初始化物理碰撞系统...");
    spark_api.changeModel(uuid, "minecraft:steve");
}
/**
 * 攻击函数，当NPC攻击时调用
 * 在这里可以添加攻击相关的逻辑
 */
function attack(event) {
    // 攻击时可以添加特殊效果或逻辑
    // 例如，可以在这里触发武器碰撞盒的特殊效果

    // 注意：实际的碰撞检测由SparkApi通过物理系统自动处理
    // 这里只是一个示例，展示如何在攻击时添加额外逻辑
}
