interface Entity {
    mobAttack(target: Entity, amount: number): void;
    hurt(damageSource: DamageSource, amount: number): void;
    getPreInput(): void;
    getDeltaMovement(): void;
    getPosition(): void;
    setCameraLock(boolean: boolean): void;
    addEffect(effect: string, duration: number, amplifier: number): void;
    addEffect(effect: string, duration: number, amplifier: number, ambient: boolean): void;
    addEffect(effect: string, duration: number, amplifier: number, ambient: boolean, visible: boolean): void;
    addEffect(effect: string, duration: number, amplifier: number, ambient: boolean, visible: boolean, showIcon: boolean): void;
}
