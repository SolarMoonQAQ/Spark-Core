// F:\temp\spark-core-test\src\main\resources\script\spark_api_test.js
var SparkAPI = Java.type("com.jme3.npc_adapter.SparkApi");
print("SparkAPI Test Script: Initializing...");
var SparkAPI = new SparkAPI()

function interact(event){
    var uuid = event.npc.getUUID();
    if (typeof SparkAPI === 'undefined') {
        print("SparkAPI Test Script: SparkAPI global object not found! Aborting tests.");
    } else {
        print("SparkAPI global object found. Proceeding with tests."+SparkAPI);
    }
    print("\n[Test Section: changeModel]");
    try {
        print("Calling SparkAPI.changeModel("+ uuid +" 'spark_core:models/new_custom_model.json')");
        SparkAPI.changeModel(uuid, "minecraft:player");
    } catch (e) {
        print("Error during changeModel tests: " + e.message);
    }
    print("\nSparkAPI Test Script: Finished.");
}



print("\n[Test Section: Collision Flags]");
var collisionModelId = "player";
var collisionBoxName = "head_hitbox";
var jsCollisionFlag = "player_collided_head";

try {
    SparkAPI.setCollisionBoxCallback(collisionModelId, collisionBoxName, jsCollisionFlag);
    print("setCollisionBoxCallback call completed.");
    var flagStatus = SparkAPI.getJsCollisionFlag(jsCollisionFlag);
    print("Initial status of flag" + jsCollisionFlag+ ": " + flagStatus);
    print("To fully test, trigger an in-game collision for the specified model and box.");
    print("Simulating a check after a hypothetical collision...");
    flagStatus = SparkAPI.getJsCollisionFlag(jsCollisionFlag);
    print("Status of flag" + jsCollisionFlag + "(after hypothetical collision)"+ "flagStatus");
    print("Resetting flag " + jsCollisionFlag + "...");
    SparkAPI.resetJsCollisionFlag(jsCollisionFlag);
    print("resetJsCollisionFlag call completed.");
    flagStatus = SparkAPI.getJsCollisionFlag(jsCollisionFlag);
    print("Status of flag" + jsCollisionFlag + "(after reset)"+ "flagStatus");
} catch (e) {
    print("Error during collision flag tests: " + e.message);
}

print("\n[Test Section: Get All and Clear Flags (Advanced)]");
try {
    print("Setting a dummy callback to potentially populate the map for testing getCollisionEventFlagsAndClear");
    SparkAPI.setCollisionBoxCallback("dummyCollisionTester", "box", "testFlagForClear");
    print("Calling SparkAPI.getCollisionEventFlagsAndClear()...");
    var allFlags = SparkAPI.getCollisionEventFlagsAndClear();
    print("All flags received: " + JSON.stringify(allFlags));
    var flagAfterClear = SparkAPI.getJsCollisionFlag("testFlagForClear");
    print("Status of flag after clear: " + flagAfterClear);
} catch (e) {
    print("Error during getCollisionEventFlagsAndClear test: " + e.message);
}
