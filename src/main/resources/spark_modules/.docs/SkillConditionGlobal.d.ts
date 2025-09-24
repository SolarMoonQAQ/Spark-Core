declare namespace SkillCondition {
    function create(name: string, reason: string, condition: (arg0: SkillHost, arg1: Level) => boolean): SkillCondition;
    function isEntity(): SkillCondition;
    function isAnimatable(): SkillCondition;
}
