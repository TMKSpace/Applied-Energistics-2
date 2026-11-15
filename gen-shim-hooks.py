# -*- coding: utf-8 -*-
"""Добор шима: CommonHooks, FakePlayer, config-screen, выравнивание FakePlayerFactory."""
import io
import os

ROOT = r"D:\Code\java\ae2-fabric-probe\compat\java"

FILES = {}

FILES["net/neoforged/neoforge/common/CommonHooks.java"] = '''/*
 * Часть nfcompat. Общие хуки NeoForge.
 *
 * В NeoForge это огромный класс (96 КБ) хуков во все места ванильного кода. AE2 зовёт
 * оттуда ровно три вещи, они и написаны:
 *  - setCraftingPlayer/getCraftingPlayer — «кто крафтит прямо сейчас», нужно рецептам,
 *    которые портят инструмент при крафте (у ванили такого понятия нет);
 *  - onLeftClickBlock — создаёт событие левого клика, AE2 сама его постит.
 */
package net.neoforged.neoforge.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

public final class CommonHooks {
    /**
     * В NeoForge это делает EventHooks через стек вызовов. У нас нет патча в
     * ванильный крафт, поэтому значение ставит и снимает сам вызывающий
     * (AE2 так и делает: setCraftingPlayer(player) ... setCraftingPlayer(null)).
     */
    private static final ThreadLocal<Player> CRAFTING_PLAYER = new ThreadLocal<>();

    private CommonHooks() {
    }

    public static void setCraftingPlayer(@Nullable Player player) {
        if (player == null) {
            CRAFTING_PLAYER.remove();
        } else {
            CRAFTING_PLAYER.set(player);
        }
    }

    @Nullable
    public static Player getCraftingPlayer() {
        return CRAFTING_PLAYER.get();
    }

    /**
     * Создаёт (но не постит) событие левого клика по блоку. В NeoForge оно
     * отправляется из патченного клиента; здесь событие строит вызывающий.
     */
    public static PlayerInteractEvent.LeftClickBlock onLeftClickBlock(Player player, BlockPos pos, Direction face,
            ServerboundPlayerActionPacket.Action action) {
        return new PlayerInteractEvent.LeftClickBlock(player, pos, face, action);
    }
}
'''

FILES["net/neoforged/neoforge/common/util/FakePlayer.java"] = '''/*
 * Часть nfcompat. Фейковый игрок.
 *
 * В NeoForge это ServerPlayer с пометкой «неродной», которого моды используют как
 * контекст действия (крафт, размещение, добыча). AE2 проверяет `instanceof FakePlayer`
 * и берёт его из Platform.getFakePlayer. Отдельного поведения классу не нужно —
 * важно, что это именно наш тип, а не любой ServerPlayer.
 */
package net.neoforged.neoforge.common.util;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class FakePlayer extends ServerPlayer {
    public FakePlayer(ServerLevel level, GameProfile profile) {
        super(level.getServer(), level, profile,
                level.getServer().getPlayerList().getPlayers().isEmpty()
                        ? null
                        : level.getServer().getPlayerList().getPlayers().get(0).clientInformation());
    }

    /** Ничего не отправляем на клиент: такого игрока там нет. */
    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isCreative() {
        return false;
    }

    @Override
    public String toString() {
        return "FakePlayer[" + getGameProfile().getName() + "]";
    }

    static {
        // ссылка, чтобы не потерять связь типов при будущем рефакторинге
        assert Player.class != null;
    }
}
'''

FILES["net/neoforged/fml/IExtensionPoint.java"] = '''/*
 * Часть nfcompat. Маркер точки расширения мода.
 *
 * В NeoForge мод регистрирует реализации таких интерфейсов у своего контейнера
 * (например, фабрику экрана конфигов). У нас контейнер их просто хранит.
 */
package net.neoforged.fml;

public interface IExtensionPoint {
}
'''

FILES["net/neoforged/neoforge/client/gui/IConfigScreenFactory.java"] = '''/*
 * Часть nfcompat. Фабрика экрана конфигов.
 *
 * В NeoForge экран конфигов открывается из списка модов. На Fabric такого списка
 * с расширениями нет, поэтому фабрика только хранится и может быть вызвана вручную.
 */
package net.neoforged.neoforge.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.IExtensionPoint;
import net.neoforged.fml.ModContainer;

@FunctionalInterface
public interface IConfigScreenFactory extends IExtensionPoint {
    Screen createScreen(ModContainer container, Screen modListScreen);
}
'''

FILES["net/neoforged/neoforge/client/gui/ConfigurationScreen.java"] = '''/*
 * Часть nfcompat. Экран конфигов.
 *
 * NeoForge'вский ConfigurationScreen — 74 КБ виджетов над ModConfigSpec. Полностью
 * портировать его сейчас незачем: у нас нет ни загрузки конфигов с диска, ни списка
 * модов, из которого он открывается. Здесь пустой экран-заглушка: он честно
 * закрывается и возвращает в родительский экран.
 *
 * TODO: заменить на реальный редактор значений, когда появится чтение/запись конфигов.
 */
package net.neoforged.neoforge.client.gui;

import net.minecraft.client.gui.GuiGraphics;
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, Component.literal("nfcompat: экран конфигов не портирован"), this.width / 2,
                this.height / 2, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
'''

for rel, text in FILES.items():
    path = os.path.join(ROOT, rel.replace("/", os.sep))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    print("написан", rel)

# --- правки существующих файлов ---

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

# FakePlayerFactory теперь отдаёт именно FakePlayer
patch("net/neoforged/neoforge/common/util/FakePlayerFactory.java",
      """        var server = level.getServer();
        if (server == null) {
            return null;
        }
        return new ServerPlayer(server, level, profile, server.getPlayerList().getPlayers().isEmpty()
                ? null
                : server.getPlayerList().getPlayers().get(0).clientInformation());""",
      """        var server = level.getServer();
        if (server == null) {
            return null;
        }
        // именно FakePlayer: AE2 проверяет instanceof этого класса
        return new FakePlayer(level, profile);""")

# ModContainer: точки расширения
patch("net/neoforged/fml/ModContainer.java",
      """    public List<ModConfig> getConfigs() {
        return configs;
    }""",
      """    public List<ModConfig> getConfigs() {
        return configs;
    }

    /** Регистрация точки расширения мода (в NeoForge — экран конфигов и т.п.). */
    public <T extends net.neoforged.fml.IExtensionPoint> T registerExtensionPoint(Class<? extends T> point) {
        return registerExtensionPoint(point, null);
    }

    public <T extends net.neoforged.fml.IExtensionPoint> T registerExtensionPoint(Class<? extends T> point, T impl) {
        if (impl != null) {
            extensions.put(point, impl);
        }
        return impl;
    }

    public <T extends net.neoforged.fml.IExtensionPoint> java.util.Optional<T> getCustomExtension(Class<T> point) {
        return java.util.Optional.ofNullable(point.cast(extensions.get(point)));
    }""")

patch("net/neoforged/fml/ModContainer.java",
      """    private final List<ModConfig> configs = new ArrayList<>();""",
      """    private final List<ModConfig> configs = new ArrayList<>();
    private final java.util.Map<Class<?>, net.neoforged.fml.IExtensionPoint> extensions = new java.util.HashMap<>();""")
