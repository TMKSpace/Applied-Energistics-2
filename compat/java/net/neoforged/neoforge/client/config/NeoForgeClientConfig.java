/*
 * Часть nfcompat. Заглушка неофорджевского клиентского конфига.
 *
 * GuideME читает `NeoForgeClientConfig.INSTANCE.handleAmbientOcclusionPerPart.get()`
 * в `GuidebookLevelRenderer.prepareMeshCache` (сборка мешей 3D-сцены гайда). На NeoForge это
 * опция «считать ambient occlusion по частям модели»; у нас такой настройки нет, поэтому
 * отдаём `true` — то есть ведём себя как NeoForge с дефолтным конфигом.
 *
 * Значение не просто `boolean`: GuideME зовёт `.get()` у `ModConfigSpec.BooleanValue`,
 * поэтому строим настоящий `BooleanValue` через `ModConfigSpec.Builder`.
 */
package net.neoforged.neoforge.client.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class NeoForgeClientConfig {

    public static final NeoForgeClientConfig INSTANCE = new NeoForgeClientConfig();

    public final ModConfigSpec.BooleanValue handleAmbientOcclusionPerPart;

    private NeoForgeClientConfig() {
        var builder = new ModConfigSpec.Builder();
        this.handleAmbientOcclusionPerPart = builder
                .comment("Compute ambient occlusion on a per-part basis (NeoForge default).")
                .define("handleAmbientOcclusionPerPart", true);
        builder.build();
    }
}
