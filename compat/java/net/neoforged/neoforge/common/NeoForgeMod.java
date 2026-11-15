/*
 * Vendored from NeoForge (LGPL-2.1-only), adapted for the Fabric shim.
 * Upstream: net/neoforged/neoforge/common/NeoForgeMod.java
 *
 * SHIM: only the pieces the shim actually needs (the mod id, used to build the ids of
 * NeoForge-provided registries such as data map types). Everything else in upstream
 * NeoForgeMod (the DeferredRegisters for fluids, menus, attachments, ...) has no
 * counterpart here, because there is no NeoForge mod loading system on Fabric.
 */

package net.neoforged.neoforge.common;

/**
 * Holds constants and registries provided by NeoForge itself.
 */
public final class NeoForgeMod {
    /**
     * The mod id NeoForge uses for everything it registers.
     */
    public static final String MOD_ID = "neoforge";

    private NeoForgeMod() {
    }
}
