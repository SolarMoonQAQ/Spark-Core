interface PhysicsCollisionObject {
    onCollide(id: string, consumer: IV8ValueObject): void;
    onAttackCollide(id: string, consumer: IV8ValueObject, customAttackSystem: AttackSystem): void;
    setCollideWithGroups(value: number): void;
    onCollisionActive(consumer: () => void): void;
    onCollisionInactive(consumer: () => void): void;
    remove(): void;
}
