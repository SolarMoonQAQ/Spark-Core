---@param skill Skill
local provider = function(skill)
    local config = function()
        local config = skill:getConfig()
        ---@type Entity
        local holder = skill:getHolder()
        config:set("test", Array:of({ { 0, 1, 2 } }))
    end

    skill:initConfig(config)
end

Skill:createBy("spirit_of_fight:sword.default.combo_2", "spark_core:test", {}, provider)