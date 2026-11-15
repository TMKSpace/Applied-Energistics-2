/*
 * Часть nfcompat. Чтение всех json-файлов каталога в карту id->объект.
 *
 * NeoForge даёт `SimpleJsonResourceReloadListener.scanDirectory(...)` как утилиту.
 * В ванили есть только сам слушатель (`prepare` — protected) и
 * `FileToIdConverter#listMatchingResources`, поэтому обход пишем здесь.
 */
package nfcompat.util;

import java.util.Map;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

public final class JsonScan {
    private JsonScan() {
    }

    public static <T> void scanDirectory(ResourceManager resourceManager, FileToIdConverter converter,
            DynamicOps<com.google.gson.JsonElement> ops, Codec<T> codec, Map<Identifier, T> output) {
        for (var entry : converter.listMatchingResources(resourceManager).entrySet()) {
            Identifier id = converter.fileToId(entry.getKey());
            try (var reader = entry.getValue().openAsReader()) {
                var json = JsonParser.parseReader(reader);
                codec.parse(ops, json)
                        .resultOrPartial(error -> System.err.println(
                                "[nfcompat] не удалось разобрать " + entry.getKey() + ": " + error))
                        .ifPresent(value -> output.put(id, value));
            } catch (Exception e) {
                System.err.println("[nfcompat] не удалось прочитать " + entry.getKey() + ": " + e);
            }
        }
    }
}
