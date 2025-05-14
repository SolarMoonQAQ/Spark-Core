var ai = Java.type("com.API.APIAi");
var math = Java.type("com.API.APIMath");
var common = Java.type("com.API.APICommon");
var stats = Java.type("com.API.APIStats");
var attributes = Java.type("com.API.APIStats.EntityAttributes");

var animation = Java.type("com.util.Chameleon.Animation");
var selector = Java.type("com.util.Chameleon.EntitySelector");
var nbt = Java.type("com.util.NBTCustom.customNbt");
var animationAPI = Java.type("mchorse.chameleon.expand.api.AnimationAPI");
var molangHelper = Java.type("mchorse.chameleon.lib.MolangHelper");

load("E:\\Minecraft\\自定义NPC客户端1.12.2\\script\\NeoMinecraft\\player.js");
load("E:\\Minecraft\\自定义NPC客户端1.12.2\\script\\NeoMinecraft\\base.js");
load("E:\\Minecraft\\自定义NPC客户端1.12.2\\script\\NeoMinecraft\\math.js");

load("E:\\Minecraft\\自定义NPC客户端1.12.2\\script\\NeoMinecraft\\gameplay\\idle.js");
load("E:\\Minecraft\\自定义NPC客户端1.12.2\\script\\NeoMinecraft\\gameplay\\attack.js");
load("E:\\Minecraft\\自定义NPC客户端1.12.2\\script\\NeoMinecraft\\gameplay\\damaged.js");
load("E:\\Minecraft\\自定义NPC客户端1.12.2\\script\\NeoMinecraft\\gameplay\\defense.js");
load("E:\\Minecraft\\自定义NPC客户端1.12.2\\script\\NeoMinecraft\\gameplay\\animation.js");