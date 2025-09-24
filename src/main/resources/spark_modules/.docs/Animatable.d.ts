interface Animatable {
    getAnimation(): AnimInstance;
    playAnimation(anim: AnimInstance, transitionTime: number): void;
    changeSpeed(time: number, speed: number): void;
}
