Skill.create("spark_core:test", [], skill => {
    skill.onStateUpdate((state) => {
        Logger.info(`${state.getName()}`)
        skill.getLevel().playSound([0.0, 0.0, 0.0], "minecraft:item.book.page_turn", "players", 1.0, 1.1)
        skill.end()
    })
})