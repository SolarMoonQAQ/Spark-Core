interface PreInput {
    execute(): void;
    execute(consumer: () => void): void;
    executeIfPresent(id: Array<String>, consumer: () => void): void;
    executeExcept(id: Array<String>, consumer: () => void): void;
}
