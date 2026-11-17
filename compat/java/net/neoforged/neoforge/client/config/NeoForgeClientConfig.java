/*
 * Часть nfcompat. Заглушка неофорджевского клиентского конфига.
 *
 * GuideME читает `NeoForgeClientConfig.INSTANCE.handleAmbientOcclusionPerPart.get()`
 * в `GuidebookLevelRenderer.prepareMeshCache` (сборка мешей 3D-сцены гайда). На NeoForge это
 * опция «считать ambient occlusion по частям модели»; у нас такой настройки нет, поэтому
 * отдаём `true` — то есть ведём себя как NeoForge с дефолтным конфигом.
 *
 * ГРАБЛИ. Мало построить `ModConfigSpec`: `ConfigValue#get()` требует, чтобы спека уже была
 * привязана к загруженному конфигу, иначе
 *
 *     IllegalStateException: Cannot get config value before config is loaded.
 *
 * Поэтому после `build()` привязываем пустой in-memory конфиг (`acceptConfig`), а `correct()`
 * внутри него дописывает дефолты по спецификации — ровно так же, как это делает
 * `ModContainer.registerConfig` с файлом на диске. Файл нам не нужен: значение всё равно
 * константное и в игре не меняется.
 */
package net.neoforged.neoforge.client.config;

import com.electronwill.nightconfig.core.CommentedConfig;

import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class NeoForgeClientConfig {

    public static final NeoForgeClientConfig INSTANCE = new NeoForgeClientConfig();

    public final ModConfigSpec.BooleanValue handleAmbientOcclusionPerPart;

    private NeoForgeClientConfig() {
        var builder = new ModConfigSpec.Builder();
        this.handleAmbientOcclusionPerPart = builder
                .comment("Compute ambient occlusion on a per-part basis (NeoForge default).")
                .define("handleAmbientOcclusionPerPart", true);
        var spec = builder.build();

        // Без этого ConfigValue#get() бросает "Cannot get config value before config is loaded".
        var config = CommentedConfig.inMemory();
        spec.acceptConfig(new IConfigSpec.ILoadedConfig() {
            @Override
            public CommentedConfig config() {
                return config;
            }

            @Override
            public void save() {
                // in-memory: сохранять некуда и незачем
            }
        });
    }
}
