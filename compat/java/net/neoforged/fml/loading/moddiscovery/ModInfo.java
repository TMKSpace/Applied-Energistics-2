/*
 * Часть nfcompat. Метаданные мода.
 *
 * В NeoForge `ModInfo` — большой record, собранный из mods.toml. У нас он собирается
 * из fabric.mod.json через FabricLoader (см. ModList) либо из явных аргументов
 * (NfCompat/ModLoader для самого AE2 и библиотек вроде GuideME).
 *
 * С 26.11 реализует `net.neoforged.neoforgespi.language.IModInfo` — этого требует
 * extendedae_plus (ModCheckUtils): он приводит элемент `ModFileInfo#getMods()` к IModInfo
 * и читает версию.
 *
 * ВАЖНО ПРО СОВМЕСТИМОСТЬ: `getVersion()` теперь возвращает maven-ный `ArtifactVersion`
 * (как в NeoForge), а не String, как было в шиме раньше. Внутри репозитория старых
 * вызовов `ModInfo#getVersion()` не осталось (проверено grep'ом), строку версии в
 * `ModList` берут из `FabricLoader` до создания ModInfo. Если где-то ещё нужна строка —
 * `getVersion().getVersionString()`.
 *
 * Чего нет: зависимостей (`getDependencies()` пуст) — в fabric.mod.json они лежат
 * в другой структуре (id/версия/optional), и разбирать их нужно вместе с FML-группой,
 * иначе получится два разных представления зависимостей.
 */
package net.neoforged.fml.loading.moddiscovery;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.neoforged.neoforgespi.language.IModInfo;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

public final class ModInfo implements IModInfo {
    private final String modId;
    private final String displayName;
    private final String version;
    private final ArtifactVersion parsedVersion;

    public ModInfo(String modId, String displayName, String version) {
        this.modId = modId;
        this.displayName = displayName;
        this.version = version == null ? "0" : version;
        this.parsedVersion = new DefaultArtifactVersion(this.version);
    }

    @Override
    public String getModId() {
        return modId;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getDescription() {
        // В fabric.mod.json описание есть, но ModInfo собирается из трёх аргументов;
        // читать его здесь неоткуда. Пустая строка - как у мода без описания.
        return "";
    }

    @Override
    public ArtifactVersion getVersion() {
        return parsedVersion;
    }

    /** Строка версии без обёртки — удобно для логов и для нашего кода. */
    public String getVersionString() {
        return version;
    }

    @Override
    public List<? extends IModInfo.ModVersion> getDependencies() {
        return List.of();
    }

    @Override
    public String getNamespace() {
        // NeoForge по умолчанию даёт "minecraft"; своего namespace в fabric.mod.json нет.
        return "minecraft";
    }

    @Override
    public Map<String, Object> getModProperties() {
        return Map.of();
    }

    @Override
    public Optional<URL> getUpdateURL() {
        return Optional.empty();
    }

    @Override
    public Optional<URL> getModURL() {
        return Optional.empty();
    }

    @Override
    public Optional<String> getLogoFile() {
        return Optional.empty();
    }

    @Override
    public boolean getLogoBlur() {
        // NeoForge: true по умолчанию (иконка размывается в списке модов).
        return true;
    }

    @Override
    public String toString() {
        return modId + "[" + version + "]";
    }
}
