/*
 * Файл nfcompat. Шим FML config.
 *
 * Загруженный конфиг: значения + путь + сам ModConfig.
 *
 * Существование этого класса - требование совместимости: ForgeConfigAPIPort
 * (его ConfigTracker/ConfigWatcher) создаёт `LoadedConfig` по имени
 * (`new LoadedConfig(CommentedConfig, Path, ModConfig)`) и читает у него `path()`.
 * Пока наш джар идёт на classpath раньше, побеждает эта версия - поэтому форма
 * record'а и аксессоров должна совпадать с их.
 *
 * `save()` всегда пишет синхронно. nightconfig 3.8 `CommentedFileConfig.of(...)`
 * отдаёт AsyncFileConfig: его `save()` ставит запись в очередь, и файл появляется
 * позже (тест сразу после save() файла не видел). Поэтому пишем сами - TOML-писателем
 * в путь, независимо от того, откуда пришёл config (наш файл или чужой Concurrent-конфиг).
 */
package net.neoforged.fml.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlFormat;

record LoadedConfig(CommentedConfig config, Path path, ModConfig modConfig) implements IConfigSpec.ILoadedConfig {

    @Override
    public void save() {
        var parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new UncheckedIOException("nfcompat: не удалось создать каталог конфига " + parent, e);
            }
        }
        TomlFormat.instance().createWriter().write(config, path, WritingMode.REPLACE);
    }
}
