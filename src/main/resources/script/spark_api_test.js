// F:\temp\spark-core-test\src\main\resources\script\spark_api_test.js

console.log("SparkAPI Test Script: Initializing...");

if (typeof SparkAPI === 'undefined') {
    console.error("SparkAPI Test Script: SparkAPI global object not found! Aborting tests.");
} else {
    console.log("SparkAPI global object found. Proceeding with tests.");
}

console.log("\n[Test Section: playAnimation]");
try {
    console.log("Calling SparkAPI.playAnimation('dummyNpc01', 'idle_loop', true)");
    SparkAPI.playAnimation("dummyNpc01", "idle_loop", true);
    console.log("Calling SparkAPI.playAnimation('dummyNpc02', 'run_cycle', false)");
    SparkAPI.playAnimation("dummyNpc02", "run_cycle", false);
    console.log("playAnimation calls completed.");
} catch (e) {
    console.error("Error during playAnimation tests: " + e.message);
}

console.log("\n[Test Section: changeModel]");
try {
    console.log("Calling SparkAPI.changeModel('dummyNpc01', 'spark_core:models/new_custom_model.json')");
    SparkAPI.changeModel("dummyNpc01", "spark_core:models/new_custom_model.json");
    console.log("changeModel call completed.");
} catch (e) {
    console.error("Error during changeModel tests: " + e.message);
}

console.log("\n[Test Section: Collision Flags]");
const collisionModelId = "player";
const collisionBoxName = "head_hitbox";
const jsCollisionFlag = "player_collided_head";

try {
    console.log(`Setting collision callback for: Model='${collisionModelId}', Box='${collisionBoxName}', Flag='${jsCollisionFlag}'`);
    SparkAPI.setCollisionBoxCallback(collisionModelId, collisionBoxName, jsCollisionFlag);
    console.log("setCollisionBoxCallback call completed.");
    let flagStatus = SparkAPI.getJsCollisionFlag(jsCollisionFlag);
    console.log(`Initial status of flag '${jsCollisionFlag}': ${flagStatus}`);
    console.log("To fully test, trigger an in-game collision for the specified model and box.");
    console.log("Simulating a check after a hypothetical collision...");
    flagStatus = SparkAPI.getJsCollisionFlag(jsCollisionFlag);
    console.log(`Status of flag '${jsCollisionFlag}' (after hypothetical collision): ${flagStatus}`);
    console.log(`Resetting flag '${jsCollisionFlag}'...`);
    SparkAPI.resetJsCollisionFlag(jsCollisionFlag);
    console.log("resetJsCollisionFlag call completed.");
    flagStatus = SparkAPI.getJsCollisionFlag(jsCollisionFlag);
    console.log(`Status of flag '${jsCollisionFlag}' after reset: ${flagStatus}`);
} catch (e) {
    console.error("Error during collision flag tests: " + e.message);
}

console.log("\n[Test Section: Get All and Clear Flags (Advanced)]");
try {
    console.log("Setting a dummy callback to potentially populate the map for testing getCollisionEventFlagsAndClear");
    SparkAPI.setCollisionBoxCallback("dummyCollisionTester", "box", "testFlagForClear");
    console.log("Calling SparkAPI.getCollisionEventFlagsAndClear()...");
    const allFlags = SparkAPI.getCollisionEventFlagsAndClear(); 
    console.log("All flags received: " + JSON.stringify(allFlags));
    const flagAfterClear = SparkAPI.getJsCollisionFlag("testFlagForClear");
    console.log(`Status of 'testFlagForClear' after getCollisionEventFlagsAndClear: ${flagAfterClear}`);
} catch (e) {
    console.error("Error during getCollisionEventFlagsAndClear test: " + e.message);
}

console.log("\nSparkAPI Test Script: Finished.");
