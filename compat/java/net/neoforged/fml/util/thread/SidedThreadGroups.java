/*
 * Часть nfcompat. Группы потоков клиента и сервера.
 *
 * В NeoForge загрузчик сам создаёт ThreadGroup для каждой стороны, а код модов сравнивает
 * Thread.currentThread().getThreadGroup() с ними. На Fabric группу серверного потока
 * создаёт сам Minecraft, поэтому SERVER здесь выставляется снаружи: на старте сервера
 * в него кладут текущую группу потока. До этого момента там заглушка.
 *
 * TODO: подключить хук на старт сервера (Fabric ServerLifecycleEvents) и заполнить SERVER.
 */
package net.neoforged.fml.util.thread;

public final class SidedThreadGroups {
    /** Группа серверного потока. Заполняется хуком на старте сервера. */
    public static volatile ThreadGroup SERVER = new ThreadGroup("nfcompat-server-pending");

    private SidedThreadGroups() {
    }

    public static void setServerThreadGroup(ThreadGroup group) {
        SERVER = group;
    }
}
