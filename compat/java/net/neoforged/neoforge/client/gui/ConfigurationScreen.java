/*
 * Часть nfcompat. Экран конфигов.
 *
 * NeoForge'вский ConfigurationScreen — 74 КБ виджетов над ModConfigSpec. Полностью
 * портировать его сейчас незачем: у нас нет ни загрузки конфигов с диска, ни списка
 * модов, из которого он открывается. Здесь пустой экран-заглушка: базовый Screen
 * сам умеет закрываться, нам достаточно держать ссылку на контейнер и родителя.
 *
 * TODO: заменить на реальный редактор значений, когда появится чтение/запись конфигов.
 */
package net.neoforged.neoforge.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import org.jetbrains.annotations.Nullable;

public class ConfigurationScreen extends Screen {
    private final ModContainer container;
    @Nullable
    private final Screen parent;

    public ConfigurationScreen(ModContainer container, @Nullable Screen parent) {
        super(Component.translatable("neoforge.configuration.title"));
        this.container = container;
        this.parent = parent;
    }

    public ModContainer getContainer() {
        return container;
    }

    @Nullable
    public Screen getParentScreen() {
        return parent;
    }
}
