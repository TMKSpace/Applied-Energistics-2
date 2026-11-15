/*
 * Часть nfcompat. Хуки геймтестов NeoForge.
 *
 * Реальные геймтесты на стороне Fabric пойдут через fabric-gametest-api; здесь
 * достаточно флага, который AE2 читает, чтобы понять, включены ли тесты.
 */
package net.neoforged.neoforge.gametest;

public final class GameTestHooks {
    private static boolean gametestEnabled = Boolean.getBoolean("neoforge.gametest.enabled");

    private GameTestHooks() {
    }

    public static boolean isGametestEnabled() {
        return gametestEnabled;
    }

    public static void setGametestEnabled(boolean enabled) {
        gametestEnabled = enabled;
    }
}
