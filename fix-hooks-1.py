# -*- coding: utf-8 -*-
"""Правки трёх новых классов шима под реальный API 26.3."""
import io
import os

ROOT = r"D:\Code\java\ae2-fabric-probe\compat\java"

def write(rel, text):
    path = os.path.join(ROOT, rel.replace("/", os.sep))
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    print("переписан", rel)

# 1) ConfigurationScreen: без рендера, только контейнер и закрытие
write("net/neoforged/neoforge/client/gui/ConfigurationScreen.java", '''/*
 * Часть nfcompat. Экран конфигов.
 *
 * NeoForge'вский ConfigurationScreen — 74 КБ виджетов над ModConfigSpec. Полностью
 * портировать его сейчас незачем: у нас нет ни загрузки конфигов с диска, ни списка
 * модов, из которого он открывается. Здесь пустой экран: он закрывается по Esc и
 * возвращает в родительский экран.
 *
 * TODO: заменить на реальный редактор значений, когда появится чтение/запись конфигов.
 */
package net.neoforged.neoforge.client.gui;

import net.minecraft.client.Minecraft;
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

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
''')

# 2) CommonHooks: маппинг ванильного Action в наш enum события
def patch(rel, old, new):
    path = os.path.join(ROOT, rel.replace("/", os.sep))
    with io.open(path, encoding="utf-8") as fh:
        text = fh.read()
    if old not in text:
        print("!! НЕ НАЙДЕНО в", rel)
        return
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text.replace(old, new, 1))
    print("поправлен", rel)

patch("net/neoforged/neoforge/common/CommonHooks.java",
      """    public static PlayerInteractEvent.LeftClickBlock onLeftClickBlock(Player player, BlockPos pos, Direction face,
            ServerboundPlayerActionPacket.Action action) {
        return new PlayerInteractEvent.LeftClickBlock(player, pos, face, action);
    }""",
      """    public static PlayerInteractEvent.LeftClickBlock onLeftClickBlock(Player player, BlockPos pos, Direction face,
            ServerboundPlayerActionPacket.Action action) {
        // в событии свой enum (START/STOP/ABORT), у ванильного пакета — свой набор
        var eventAction = switch (action) {
            case START_DESTROY_BLOCK -> PlayerInteractEvent.LeftClickBlock.Action.START;
            case STOP_DESTROY_BLOCK -> PlayerInteractEvent.LeftClickBlock.Action.STOP;
            default -> PlayerInteractEvent.LeftClickBlock.Action.ABORT;
        };
        return new PlayerInteractEvent.LeftClickBlock(player, pos, face, eventAction);
    }""")

# 3) FakePlayer: getGameProfile() в 26.3 нет
patch("net/neoforged/neoforge/common/util/FakePlayer.java",
      """    @Override
    public String toString() {
        return "FakePlayer[" + getGameProfile().getName() + "]";
    }""",
      """    @Override
    public String toString() {
        return "FakePlayer[" + getUUID() + "]";
    }""")
