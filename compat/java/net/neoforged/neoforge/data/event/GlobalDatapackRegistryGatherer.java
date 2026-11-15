/*
 * Vendored from NeoForge (LGPL-2.1-only), adapted for the Fabric shim.
 * Upstream: net/neoforged/neoforge/data/event/GlobalDatapackRegistryGatherer.java
 */

package net.neoforged.neoforge.data.event;

/**
 * Gathers the datapack registry entries during {@link GatherDataRegistryEntriesEvent} within a global registry set
 * shared between all mods.
 */
public interface GlobalDatapackRegistryGatherer extends DatapackRegistryGatherer {
    /**
     * Gathers the datapack registry entries for a subset of mod ids different from the caller's mod id.
     *
     * <p>Shim note: all entries end up in one registry set, so this only records the requested mod ids.
     *
     * @param modId  The first mod id to gather registry entries for.
     * @param modIds Any additional mod ids to gather the registry entries for.
     * @return A gatherer for the specified mod ids.
     */
    DatapackRegistryGatherer gatherFor(String modId, String... modIds);
}
