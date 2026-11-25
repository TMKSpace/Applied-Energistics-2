/*
 * Часть nfcompat. Шим `net.neoforged.neoforgespi.language.IModInfo`.
 *
 * Сверено с апстримом FancyModLoader (loader/src/main/java/net/neoforged/neoforgespi/language/IModInfo.java):
 * это интерфейс метаданных мода из mods.toml. У нас метаданные приходят из
 * fabric.mod.json (см. net.neoforged.fml.loading.moddiscovery.ModInfo).
 *
 * Кому нужно: extendedae_plus (ModCheckUtils) берёт `IModInfo` из `ModFileInfo#getMods()`
 * и зовёт `getVersion()`, дальше `ArtifactVersion.toString()`. Именно из-за дескриптора
 * `getVersion()` возвращает maven-ный `ArtifactVersion` — см. ArtifactVersion.java.
 *
 * ЧТО ВЗЯТО: getModId/getDisplayName/getDescription/getVersion/getDependencies/
 * getNamespace/getModProperties/getUpdateURL/getModURL/getLogoFile/getLogoBlur,
 * вложенные `Ordering`, `DependencyType`, `DependencySide`, `ModVersion`.
 *
 * ЧЕГО НЕТ (сознательно, с причиной):
 *  - `getOwningFile()`, `getLoader()`, `getConfig()`, `getForgeFeatures()` — их типы
 *    (IModFileInfo, IModLanguageLoader, IConfigurable, ForgeFeature.Bound) — внутренности
 *    загрузчика FML, которого в шиме нет. Это отдельная (FML) группа, её делают своим
 *    заходом; добавлять сюда полудохлые заглушки под них смысла нет.
 *  - `UNBOUNDED` (VersionRange из maven-artifact) и `ModVersion#getVersionRange()`:
 *    второго maven-класса (VersionRange) в шиме нет; диапазоны версий сейчас никто
 *    не спрашивает. Когда появятся — добавлять вместе с VersionRange, а не возвращать
 *    строку (это сломало бы дескриптор).
 *  - `DependencySide#isCorrectSide()` в апстриме лезет в `FMLLoader` (которого мы
 *    намеренно не отдаём — через него чужие моды определяют «мы на NeoForge»).
 *    Здесь сторона берётся из `FMLEnvironment.dist` — тот же смысл, без FMLLoader.
 */
package net.neoforged.neoforgespi.language;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.apache.maven.artifact.versioning.ArtifactVersion;

public interface IModInfo {

    /** Идентификатор мода (у нас — id из fabric.mod.json). */
    String getModId();

    /** Отображаемое имя. */
    String getDisplayName();

    /** Описание мода. */
    String getDescription();

    /** Версия. Тип — maven-ный, как в NeoForge (см. ArtifactVersion.java). */
    ArtifactVersion getVersion();

    /** Зависимости. У нашей реализации список пуст: в fabric.mod.json они другого вида. */
    List<? extends ModVersion> getDependencies();

    /** Пространство имён ресурсов мода (в NeoForge по умолчанию "minecraft"). */
    String getNamespace();

    /** Свободные свойства из метаданных. */
    Map<String, Object> getModProperties();

    Optional<URL> getUpdateURL();

    Optional<URL> getModURL();

    Optional<String> getLogoFile();

    boolean getLogoBlur();

    enum Ordering {
        BEFORE, AFTER, NONE
    }

    enum DependencyType {
        REQUIRED, OPTIONAL,
        /** Запрещает загрузку, если зависимость есть. */
        INCOMPATIBLE,
        /** Предупреждает, если зависимость есть. */
        DISCOURAGED
    }

    enum DependencySide {
        CLIENT(Dist.CLIENT), SERVER(Dist.DEDICATED_SERVER), BOTH(Dist.values());

        private final Dist[] dist;

        DependencySide(Dist... dist) {
            this.dist = dist;
        }

        public boolean isContained(Dist side) {
            return this == BOTH || dist[0] == side;
        }

        /** В NeoForge спрашивает FMLLoader; у нас — FMLEnvironment (см. шапку). */
        public boolean isCorrectSide() {
            return this == BOTH || FMLEnvironment.dist.equals(this.dist[0]);
        }
    }

    interface ModVersion {
        String getModId();

        DependencyType getType();

        /** Причина; показывается только для DISCOURAGED/INCOMPATIBLE. */
        Optional<String> getReason();

        Ordering getOrdering();

        DependencySide getSide();

        void setOwner(IModInfo owner);

        IModInfo getOwner();

        Optional<URL> getReferralURL();
    }
}
