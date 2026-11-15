# -*- coding: utf-8 -*-
"""ConfigurationScreen: убираем onClose (в ванили закрытие уже умеет)."""
import io

PATH = r"D:\Code\java\ae2-fabric-probe\compat\java\net\neoforged\neoforge\client\gui\ConfigurationScreen.java"

TEXT = '''/*
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
'''

with io.open(PATH, "w", encoding="utf-8", newline="\n") as fh:
    fh.write(TEXT)
print("ConfigurationScreen переписан")
