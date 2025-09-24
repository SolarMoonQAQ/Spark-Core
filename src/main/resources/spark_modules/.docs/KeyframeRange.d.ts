interface KeyframeRange {
    onEnter(handler: KeyframeRange.(Enter) -> Unit): void;
    onInside(handler: KeyframeRange.(Inside) -> Unit): void;
    onExit(handler: KeyframeRange.(Exit) -> Unit): void;
}
