interface AnimInstance {
    getProgress(): void;
    onSwitchIn(consumer: (arg0: AnimInstance) => void): void;
    onSwitchOut(consumer: (arg0: AnimInstance) => void): void;
    onEnd(consumer: () => void): void;
    onCompleted(consumer: () => void): void;
    registerKeyframeRangeEnd(id: string, end: number): KeyframeRange;
    registerKeyframeRangeStart(id: string, start: number): KeyframeRange;
    registerKeyframeRanges(id: string, range: number[][], provider: (arg0: KeyframeRange, arg1: number) => void): KeyframeRange[];
}
