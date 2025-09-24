declare namespace PhysicsCollisionObject {
    function createCollisionBoxBoundToBone(animatable: IAnimatable<any>, boneName: string, size: number[], offset: number[], init: (arg0: (PhysicsCollisionObject) => Unit)): PhysicsCollisionObject;
}
