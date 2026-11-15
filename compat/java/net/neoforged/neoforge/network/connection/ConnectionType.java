/*
 * Часть nfcompat. Заглушка NeoForge-овского ConnectionType.
 *
 * В NeoForge этот enum отвечает за то, умеет ли противоположная сторона
 * Neo-овский протокол (Negotiation). В NeoForge значение живёт прямо в
 * RegistryFriendlyByteBuf (поле connectionType), и весь код вида
 * "new RegistryFriendlyByteBuf(buf, registryAccess, ConnectionType.NEOFORGE)"
 * этим пользуется.
 *
 * На Fabric поля в RegistryFriendlyByteBuf нет и быть не может (это класс MC,
 * а не шим), поэтому здесь остаётся только сам enum как тип для тех вызовов,
 * которые компилятор ещё способен принять. Реально подключить значение к
 * буферу нельзя - см. отчёт: те места AE2 придётся править на стороне AE2.
 *
 * Семантически на Fabric любое соединение - это OTHER: сокета с Neo-овским
 * переговорщиком тут не бывает, каналы регистрируются через Fabric API.
 */
package net.neoforged.neoforge.network.connection;

public enum ConnectionType {
    /** Другая сторона разговаривает по Neo-овскому протоколу (на Fabric не встречается). */
    NEOFORGE,

    /** Ванильное соединение или другая платформа (Fabric, Bukkit и т.п.). */
    OTHER;

    public boolean isOther() {
        return this == OTHER;
    }

    public boolean isNeoForge() {
        return this == NEOFORGE;
    }

    /**
     * nfcompat: тип соединения, который реально действует на этой платформе.
     * В NeoForge эквивалента нет - там тип определяется переговорами при коннекте.
     */
    public static ConnectionType current() {
        return OTHER;
    }
}
