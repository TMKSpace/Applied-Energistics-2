/*
 * Часть nfcompat (GuideME). `SimpleJsonResourceReloadListener.scanDirectory` без NeoForge.
 *
 * ЗАЧЕМ. GuideME (сборка под NeoForge) в `GuideReloadListener.loadDataDrivenGuides` зовёт
 * статический `scanDirectory(ResourceManager, FileToIdConverter, DynamicOps, Codec, Map)`.
 * Такого метода NeoForge добавляет патчем; в ванили 26.3 у `SimpleJsonResourceReloadListener`
 * вообще нет `scanDirectory` (остался только `prepare`). Без него падает загрузка гайдов:
 *
 *     NoSuchMethodError: SimpleJsonResourceReloadListener.scanDirectory(...)
 *       at guideme.internal.GuideReloadListener.loadDataDrivenGuides
 *
 * ЧТО ХУЖЕ ОШИБКИ. Исключение в reload-слушателе обрывает ВЕСЬ перезагруз ресурспаков:
 * клиент снимает выбранные паки и уходит в recovery-экран, а там Jade на первом же экране
 * падает на своём assert про отсутствующие переводы конфигов (переводы не доехали, потому что
 * reload не состоялся). То есть один отсутствующий метод валит клиент целиком.
 *
 * ЧТО ДЕЛАЕМ. Повторяем семантику NeoForge: пройти по всем json'ам в каталоге, распарсить
 * кодеком и сложить в переданную карту (пятый аргумент — «куда писать», GuideME передаёт
 * свою карту). Ошибки парсинга логируем и пропускаем, как ванильный `prepare`.
 */
package nfcompat.compat.guideme;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GuideMEReloadCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("nfcompat/guideme");

    private GuideMEReloadCompat() {
    }

    /**
     * Аналог неофорджевского `SimpleJsonResourceReloadListener.scanDirectory`.
     *
     * @param into карта, в которую пишем; {@code null} — создать новую
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <T> Map<Identifier, T> scanDirectory(ResourceManager resourceManager,
            FileToIdConverter lister,
            DynamicOps<JsonElement> ops,
            Codec<T> codec,
            Map<Identifier, T> into) {
        var result = into != null ? into : new HashMap<Identifier, T>();

        for (var entry : lister.listMatchingResources(resourceManager).entrySet()) {
            var file = entry.getKey();
            var id = lister.fileToId(file);
            try (var reader = entry.getValue().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                ((com.mojang.serialization.Codec) codec).parse(ops, json)
                        .resultOrPartial(error -> LOGGER.error("Couldn't parse {}: {}", file, error))
                        .ifPresent(value -> result.put(id, (T) value));
            } catch (Exception e) {
                LOGGER.error("Couldn't read {}", file, e);
            }
        }

        return result;
    }
}
