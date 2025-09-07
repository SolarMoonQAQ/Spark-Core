---@class PhysicsCollisionObject
---@field onCollide fun(self:PhysicsCollisionObject, id:string, consumer:LuaValueProxy) 
---@field onAttackCollide fun(self:PhysicsCollisionObject, id:string, consumer:LuaValueProxy, customAttackSystem:AttackSystem?) 
---@field setCollideWithGroups fun(self:PhysicsCollisionObject, value:number) 
---@field onCollisionActive fun(self:PhysicsCollisionObject, consumer:LuaValueProxy) 
---@field onCollisionInactive fun(self:PhysicsCollisionObject, consumer:LuaValueProxy) 
---@field remove fun(self:PhysicsCollisionObject) 

