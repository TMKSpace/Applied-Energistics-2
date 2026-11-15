/*
 * Часть nfcompat. Сторона, на которой загружен мод.
 *
 * В NeoForge это часть API загрузчика; на Fabric аналог — EnvType, но константы те же две.
 */
package net.neoforged.api.distmarker;

public enum Dist {
    CLIENT,
    DEDICATED_SERVER;

    public boolean isClient() {
        return this == CLIENT;
    }

    public boolean isDedicatedServer() {
        return this == DEDICATED_SERVER;
    }
}
