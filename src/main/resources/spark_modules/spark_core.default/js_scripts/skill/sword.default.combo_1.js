Skill.create("spark_core:test", [], skill => {
    skill.onStateUpdate((state) => {
        const pos = skill.getHolder().position()
        skill.getLevel().playSound([pos.x, pos.y, pos.z], "minecraft:item.book.page_turn", "players", 1.0, 1.1)
        skill.getLevel().summonSpaceWarp([pos.x, pos.y + 1, pos.z], 0.0, 2.0, 5.0, 20)
        skill.end()
    })
})