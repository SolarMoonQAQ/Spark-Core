declare namespace Skill {
    /**
     * 创建技能
     * @param id 技能id
     * @return 技能类型
     */
    function create(id: string, conditions: SkillCondition[], provider: (arg0: Skill) => void): SkillType<any>;
    function createBy(id: string, extend: string, conditions: SkillCondition[], provider: (arg0: Skill) => void): SkillType<any>;
}
