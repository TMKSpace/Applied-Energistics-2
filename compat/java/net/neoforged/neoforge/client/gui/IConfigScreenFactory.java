/*
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
