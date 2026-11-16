/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.capabilities;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Хелпер для повторяющихся запросов block capability в фиксированной позиции с фиксированным
 * контекстом.
 *
 * <h3>nfcompat: этот класс намеренно НЕ кэширует</h3>
 *
 * <p>В NeoForge он держит найденный объект и сбрасывает его по
 * {@code Level#invalidateCapabilities(BlockPos)}. У нас такого хука нет (Level не патчится,
 * а {@code nfcompat.capabilities.CapabilityInvalidation} — no-op), поэтому любой кэш здесь
 * означал бы устаревшие хендлеры: соседний блок сменился или обновил состояние, а шина /
 * провайдер шаблонов продолжает держать мёртвый {@code IItemHandler}/{@code MEStorage}.
 * Для экспорт-шины это прямой путь к потере предметов.
 *
 * <p>Поэтому {@link #getCapability()} каждый раз идёт в
 * {@code capability.getCapability(level, pos, null, null, context)} — ровно так же, как
 * Fabric {@code BlockApiLookup}, у которого кэша нет вовсе.
 *
 * <p>Компромисс осознанный: чуть больше запросов на тик (одно обращение к провайдерам на
 * каждый вызов вместо одного раза на смену блока), зато гарантированно нет устаревших
 * хендлеров. Профилировки, которая показала бы, что это горячая точка, пока нет; если
 * появится — вернёмся к кэшу, но уже с реальной инвалидацией (хук на смену блока /
 * блок-сущности), а не с флагом «первый ответ валиден навсегда».
 *
 * <p>Публичный API сохранён как в NeoForge, чтобы код в {@code src/} компилировался без
 * правок: {@link #create}, {@link #getCapability}, {@link #level()}, {@link #pos()},
 * {@link #context()}. Перегрузка {@code create(..., isValid, invalidationListener)}
 * принимает аргументы и игнорирует их: без кэша инвалидировать нечего, поэтому слушатель
 * не вызывается никогда. (Он и раньше не вызывался: регистрации слушателей в Level у нас
 * нет, поле слушателя просто лежало без дела.)
 */
public final class BlockCapabilityCache<T, C extends @Nullable Object> {
    /**
     * Creates a new cache instance for the given position and context.
     *
     * <p>nfcompat: «cache» только по названию — объект не хранит найденную capability.
     *
     * @param capability the capability
     * @param level      the level
     * @param pos        the position
     * @param context    extra context for the query
     */
    public static <T, C extends @Nullable Object> BlockCapabilityCache<T, C> create(BlockCapability<T, C> capability, ServerLevel level, BlockPos pos, C context) {
        Objects.requireNonNull(capability);
        Objects.requireNonNull(level);
        Objects.requireNonNull(pos);

        return new BlockCapabilityCache<>(capability, level, pos.immutable(), context);
    }

    /**
     * Creates a new cache instance with an invalidation listener, and registers it to the level.
     *
     * <p>nfcompat: {@code isValid} и {@code invalidationListener} принимаются только ради
     * совместимости с NeoForge-ным API и не используются — кэша нет, инвалидация не нужна,
     * уведомлять слушателя не о чем (см. javadoc класса).
     *
     * @param capability           the capability
     * @param level                the level
     * @param pos                  the position
     * @param context              extra context for the query
     * @param isValid              ignored: no cache, so no notifications to filter
     * @param invalidationListener ignored: no cache, so nothing ever gets invalidated
     */
    public static <T, C extends @Nullable Object> BlockCapabilityCache<T, C> create(BlockCapability<T, C> capability, ServerLevel level, BlockPos pos, C context, BooleanSupplier isValid, Runnable invalidationListener) {
        Objects.requireNonNull(isValid);
        Objects.requireNonNull(invalidationListener);

        return create(capability, level, pos, context);
    }

    private final BlockCapability<T, C> capability;
    private final ServerLevel level;
    private final BlockPos pos;
    private final C context;

    private BlockCapabilityCache(BlockCapability<T, C> capability, ServerLevel level, BlockPos pos, C context) {
        this.capability = capability;
        this.level = level;
        this.pos = pos;
        this.context = context;
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos pos() {
        return pos;
    }

    public C context() {
        return context;
    }

    /**
     * Gets the capability instance, or {@code null} if the capability is not present.
     *
     * <p>If {@linkplain #pos() the target position} is not loaded, this method will return
     * {@code null} — как и в NeoForge, в выгруженный чанк не лезем.
     *
     * <p>Результат не запоминается: следующий вызов спросит провайдеров заново.
     */
    @Nullable
    public T getCapability() {
        if (!level.isLoaded(pos)) {
            // If the position is not loaded, return no capability for now.
            return null;
        }

        // nfcompat: level.getCapability(...) — NeoForge-ный патч на Level, поэтому
        // идём напрямую через capability.getCapability(...).
        return capability.getCapability(level, pos, null, null, context);
    }
}
