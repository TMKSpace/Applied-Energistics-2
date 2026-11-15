/*
 * Adapted from NeoForge (LGPL-2.1-only) for the Fabric shim.
 * Upstream: net/neoforged/neoforge/data/loading/DatagenModLoader.java
 *
 * SHIM: the shim has no datagen bootstrap. Fabric datagen runs through fabric-data-generation-api-v1, so nothing
 * flips this flag; {@link #isRunningDataGen()} is always {@code false} and {@link #begin} refuses to run.
 */

package net.neoforged.neoforge.data.loading;

/**
 * Entry point of NeoForge's datagen runtime.
 */
public final class DatagenModLoader {
    private static volatile boolean runningDataGen;

    private DatagenModLoader() {}

    public static boolean isRunningDataGen() {
        return runningDataGen;
    }

    /**
     * Marks the shim as being in a datagen run. Nothing calls this automatically; it exists so that hosts that do
     * drive generation through the shim's events can suppress client-only side effects.
     */
    public static void setRunningDataGen(boolean running) {
        runningDataGen = running;
    }

    /**
     * Shim note: NeoForge's datagen bootstrap (mod filtering, registry gathering, provider dispatch) is not
     * implemented. Fabric's own data generation API must be used instead.
     *
     * @throws UnsupportedOperationException always
     */
    public static void begin(Object... ignored) {
        throw new UnsupportedOperationException(
                "DatagenModLoader#begin is a NeoForge datagen bootstrap; use fabric-data-generation-api-v1 instead");
    }
}
