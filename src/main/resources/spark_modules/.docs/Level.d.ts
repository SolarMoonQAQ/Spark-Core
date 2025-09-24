interface Level {
    playSound(pos: number[], sound: string, source: string): void;
    playSound(pos: number[], sound: string, source: string, volume: number, pitch: number): void;
    playSound(pos: BlockPos, sound: string, source: string): void;
    playSound(pos: BlockPos, sound: string, source: string, volume: number, pitch: number): void;
}
