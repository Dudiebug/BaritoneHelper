package dev.dudie.baritonehelper.client;

final class DashboardInput {
    private DashboardInput() {
    }

    static boolean isSignedInteger(String value) {
        if (value == null || value.isEmpty() || value.equals("-")) return true;
        int start = value.charAt(0) == '-' ? 1 : 0;
        return start < value.length()
                && value.substring(start).chars().allMatch(Character::isDigit);
    }
}
