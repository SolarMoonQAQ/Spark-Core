interface Skill {
    addTarget(entity: Entity): void;
    removeTarget(entity: Entity): void;
    end(): void;
    getHolder(): SkillHost;
    getLevel(): Level;
    initConfig(consumer: (arg0: SkillConfig) => void): void;
    init(consumer: () => void): void;
    onStart(consumer: () => void): void;
    onStateEnter(consumer: (arg0: SkillState) => void): void;
    onStateUpdate(consumer: (arg0: SkillState) => void): void;
    onStateExit(consumer: (arg0: SkillState) => void): void;
    onEnd(consumer: () => void): void;
    onLocalInputUpdate(consumer: (arg0: MovementInputUpdateEvent) => void): void;
    onTargetHurt(consumer: (arg0: LivingIncomingDamageEvent) => void): void;
    onTargetActualHurtPre(consumer: (arg0: Pre) => void): void;
    onTargetActualHurtPost(consumer: (arg0: Post) => void): void;
    onHurt(consumer: (arg0: LivingIncomingDamageEvent) => void): void;
    onTargetKnockBack(consumer: (arg0: LivingKnockBackEvent) => void): void;
}
