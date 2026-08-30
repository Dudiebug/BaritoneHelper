package dev.dudie.buddybot.logic;

public final class TemporaryBlockGuard {
    private TemporaryBlockGuard() {}

    public static boolean canRestore(String placedState, String currentState) {
        return placedState != null && placedState.equals(currentState);
    }
}
