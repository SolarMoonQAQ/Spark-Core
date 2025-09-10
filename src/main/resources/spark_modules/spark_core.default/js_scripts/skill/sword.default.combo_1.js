Skill.create("spark_core:test", [], skill => {
    skill.onStateUpdate((state) => {
        Logger.info(`${state.getName()}`)
        skill.end()
    })
})