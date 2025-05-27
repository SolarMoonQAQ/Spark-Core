/**
 * Spark Core 物理碰撞功能示例
 * 
 * 本示例展示如何在CustomNPCs的JavaScript脚本中使用SparkApi提供的物理碰撞功能。
 * 包括创建碰撞盒、注册碰撞回调和处理碰撞事件等功能。
 */
var SparkAPI = Java.type("com.jme3.npc_adapter.SparkCustomnpcApi");
print("SparkAPI Test Script: Initializing...");
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
    spark_api.registerEntityAnimationOverride(uuid, "spark_core:walk", "steve/walk");
    spark_api.registerEntityAnimationOverride(uuid, "spark_core:idle", "steve/idle");
    try {
        // 需要延迟注册
        if (is_init) {
            // 注册普通碰撞回调
            spark_api.registerCollisionCallback(weaponCollisionBoxId, WEAPON_COLLISION_EVENT_ID);
            // 注册攻击碰撞回调
            spark_api.registerAttackCollisionCallback(weaponCollisionBoxId, WEAPON_ATTACK_EVENT_ID, "steve:attack", "steve:hurt");
            // 注册普通碰撞回调
            spark_api.registerCollisionCallback(shieldCollisionBoxId, SHIELD_COLLISION_EVENT_ID);

            npc.say("碰撞盒已注册");
            is_init = false;
        }else {
            // 为NPC的右手创建武器碰撞盒
            // 参数：modelId, boneName, sizeX, sizeY, sizeZ, offsetX, offsetY, offsetZ
            weaponCollisionBoxId = spark_api.createCollisionBoxBoundToBone(
                npc.getUUID(),  // 使用NPC的UUID作为模型ID
                "rightForeArm",    // 绑定到右手骨骼
                1, 1, 1,  // 碰撞盒尺寸（米）
                0.3, 0, 0       // 相对于骨骼的偏移（米）
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
                0.2, 0, 0       // 相对于骨骼的偏移（米）
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
    try {
        var npc = event.npc;
        var world = npc.word;
        // 处理武器普通碰撞事件
        if (event.id === WEAPON_COLLISION_EVENT_ID) {
            var type = event.arguments[0];       // 碰撞类型: "start", "processed", "end"
            var otherName = event.arguments[1];  // 另一个碰撞体的名称
            var manifoldId = event.arguments[2]; // 碰撞点ID

            if (type === "start") {
                npc.say("武器开始碰撞: " + otherName);
            } else if (type === "processed") {
                // 获取碰撞点位置
                var posA = spark_api.getContactPosA(manifoldId);

                // 在碰撞点位置创建粒子效果（示例）
                if (world && posA) {
                    world.spawnParticle("minecraft:crit", posA[0], posA[1], posA[2], 5, 0.1, 0.1, 0.1, 0.05);
                }
            } else if (type === "end") {
                npc.say("武器结束碰撞: " + otherName);
            }
        }

        // 处理武器攻击碰撞事件
        else if (event.id === WEAPON_ATTACK_EVENT_ID) {
            var phase = event.arguments[0];      // 攻击阶段: "preAttack", "doAttack", "postAttack"

            if (phase === "preAttack") {
                var isFirst = event.arguments[1];    // 是否首次攻击
                var targetId = event.arguments[2];   // 目标实体ID
                var manifoldId = event.arguments[3]; // 碰撞点ID

                npc.say("武器攻击前: " + (isFirst ? "首次攻击" : "连续攻击") + " 目标ID: " + targetId);
            } else if (phase === "doAttack") {
                var targetId = event.arguments[1];   // 目标实体ID
                var manifoldId = event.arguments[2]; // 碰撞点ID

                // 获取碰撞点位置
                var posB = spark_api.getContactPosB(manifoldId);

                npc.say("武器攻击中: 目标ID: " + targetId + " 位置: " + posB[0].toFixed(2) + ", " + posB[1].toFixed(2) + ", " + posB[2].toFixed(2));

                // 在碰撞点位置创建粒子效果（示例）
                if (world && posB) {
                    world.spawnParticle("minecraft:flame", posB[0], posB[1], posB[2], 10, 0.2, 0.2, 0.2, 0.05);
                }
            } else if (phase === "postAttack") {
                var targetId = event.arguments[1];   // 目标实体ID
                var manifoldId = event.arguments[2]; // 碰撞点ID

                npc.say("武器攻击后: 目标ID: " + targetId);
            }
        }

        // 处理盾牌碰撞事件
        else if (event.id === SHIELD_COLLISION_EVENT_ID) {
            var type = event.arguments[0];       // 碰撞类型: "start", "processed", "end"
            var otherName = event.arguments[1];  // 另一个碰撞体的名称
            var manifoldId = event.arguments[2]; // 碰撞点ID

            if (type === "processed") {
                npc.say("盾牌格挡: " + otherName);

                // 获取碰撞点位置
                var posA = spark_api.getContactPosA(manifoldId);

                // 在碰撞点位置创建粒子效果（示例）
                if (world && posA) {
                    world.spawnParticle("minecraft:smoke", posA[0], posA[1], posA[2], 5, 0.1, 0.1, 0.1, 0.01);
                }
            }
        }
    } catch (e) {
        npc.say("处理碰撞事件时发生错误: " + e.message);
    }
}

/**
 * 交互函数，当玩家与NPC交互时调用
 * 在这里添加测试功能
 */
function init(event) {
    var player = event.player;
    var npc = event.npc;
    // 显示当前碰撞盒状态
    player.message("§a武器碰撞盒ID: §f" + (weaponCollisionBoxId || "未创建"));
    player.message("§a盾牌碰撞盒ID: §f" + (shieldCollisionBoxId || "未创建"));
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
