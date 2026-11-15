package net.neoforged.fml;

/**
 * Сторона, на которой выполняется код: клиент или сервер.
 */
public enum LogicalSide {
    CLIENT,
    SERVER;

    public boolean isClient() {
        return this == CLIENT;
    }

    public boolean isServer() {
        return this == SERVER;
    }
}
