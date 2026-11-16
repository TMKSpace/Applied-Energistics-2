/*
 * Часть nfcompat. Пакет синхронизации рецептов (сервер -> клиент).
 *
 * Содержимое — ванильный `RecipeHolder.STREAM_CODEC`, то есть рецепты едут в том же
 * виде, в каком их передаёт ванильный update_recipes, со всеми компонентами и тегами.
 * Карта режется на порции, `last` отмечает последнюю — клиент собирает их подряд.
 */
package nfcompat.network;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;

public record RecipesSyncPayload(List<RecipeHolder<?>> recipes, boolean last) implements CustomPacketPayload {

    public static final Type<RecipesSyncPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("nfcompat", "recipes"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipesSyncPayload> CODEC = StreamCodec.composite(
            RecipeHolder.STREAM_CODEC.apply(ByteBufCodecs.list()), RecipesSyncPayload::recipes,
            ByteBufCodecs.BOOL, RecipesSyncPayload::last,
            RecipesSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
