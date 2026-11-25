/*
 * Часть nfcompat. Терпимое чтение BlockState в формате до MC 26.3.
 *
 * В 26.3 StateHolder.codec() переехал на dispatch по "id":
 *     {id: "minecraft:stone", properties: {facing: "north"}}
 * (а состояние по умолчанию вообще пишется строкой: "minecraft:stone").
 * До этого BlockState.CODEC был записью:
 *     {Name: "minecraft:stone", Properties: {facing: "north"}}
 *
 * Этим старым форматом до сих пор записаны:
 *   - guidebook/assets/assemblies/*.snbt - сцены гайдбука (фасады в них лежат
 *     как facadeUp: {Name: "minecraft:stone"});
 *   - spatial-ячейки и любые structure-шаблоны, сохранённые до 26.3.
 *
 * Поэтому читаем фасады через either(ванильный BlockState.CODEC, старый формат):
 * новый кодек пробуется первым, старый - фоллбэком. Ошибки первой ветки
 * Codec.either проглатывает, так что StructureTemplate больше не сыплет
 * "Failed to parse either. First: Not a string; Second: No key id in MapLike".
 * Запись при этом всегда идёт ванильным кодеком (xmap кладёт значение в Either.left),
 * чтобы не плодить устаревший формат в новых мирах.
 */
package nfcompat.util;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public final class LegacyBlockStateCodec {
    /**
     * Формат BlockState до MC 26.3: {Name: "minecraft:stone", Properties: {...}}.
     * Properties необязательны и содержат только свойства, отличные от дефолтных.
     */
    private static final Codec<BlockState> LEGACY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.BLOCK.byNameCodec().fieldOf("Name").forGetter(BlockState::getBlock),
            Codec.unboundedMap(Codec.STRING, Codec.STRING)
                    .optionalFieldOf("Properties", Map.of())
                    .forGetter(LegacyBlockStateCodec::getProperties))
            .apply(instance, LegacyBlockStateCodec::createState));

    /**
     * Чтение: сначала новый формат, при неудаче - старый. Запись: всегда новый формат.
     */
    public static final Codec<BlockState> CODEC = Codec.either(BlockState.CODEC, LEGACY_CODEC)
            .xmap(LegacyBlockStateCodec::unwrap, state -> Either.<BlockState, BlockState>left(state));

    private static BlockState unwrap(Either<BlockState, BlockState> either) {
        return either.left().orElseGet(() -> either.right().orElseThrow());
    }

    private static Map<String, String> getProperties(BlockState state) {
        var result = new LinkedHashMap<String, String>();
        state.getValues().forEach(value -> result.put(value.property().getName(), value.valueName()));
        return result;
    }

    private static BlockState createState(Block block, Map<String, String> properties) {
        var state = block.defaultBlockState();
        var definition = block.getStateDefinition();
        for (var entry : properties.entrySet()) {
            // Неизвестное свойство не повод терять весь фасад - просто пропускаем его
            var property = definition.getProperty(entry.getKey());
            if (property != null) {
                state = applyProperty(state, property, entry.getValue());
            }
        }
        return state;
    }

    private static BlockState applyProperty(BlockState state, Property<?> property, String value) {
        return applyPropertyTyped(state, property, value);
    }

    private static <T extends Comparable<T>> BlockState applyPropertyTyped(BlockState state, Property<T> property,
            String value) {
        return property.getValue(value)
                .map(parsed -> state.setValue(property, parsed))
                .orElse(state);
    }

    private LegacyBlockStateCodec() {
    }
}
