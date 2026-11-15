/*
 * Часть nfcompat. «На каком мы потоке» — без NeoForge'вских ThreadGroup.
 *
 * NeoForge сам заводит отдельную ThreadGroup для серверных потоков, и AE2 сравнивает
 * Thread.currentThread().getThreadGroup() с ней (appeng.util.Platform.isServer/isClient/
 * assertServerThread). На Fabric группы другие: в одиночной игре серверный поток живёт
 * в ТОЙ ЖЕ группе, что и главный/рендер-поток, поэтому сравнение по группе врёт — и
 * isClient() на клиенте возвращает false. Симптом был заметный: у ячеек хранения
 * пропадали строки «Bytes Used / Types» в тултипе, потому что их добавление обёрнуто
 * в `if (Platform.isClient())`.
 *
 * Поэтому сравниваем не группу, а сам поток: интегрированный сервер запоминает свой
 * поток на первом тике, а на выделенном сервере серверная сторона — всегда «сервер».
 */
package nfcompat;

import net.fabricmc.loader.api.FabricLoader;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class ServerThread {
    private static volatile Thread thread;
    private static final boolean DEDICATED;

    static {
        DEDICATED = FabricLoader.getInstance().getEnvironmentType() != net.fabricmc.api.EnvType.CLIENT
                || FMLEnvironment.dist == Dist.DEDICATED_SERVER;
    }

    private ServerThread() {
    }

    /** Запоминает текущий поток как серверный (вызывается с тика сервера). */
    public static void adopt() {
        thread = Thread.currentThread();
    }

    /** {@return true, если код исполняется на серверной стороне} */
    public static boolean isServer() {
        if (DEDICATED) {
            return true;
        }
        return thread != null && thread == Thread.currentThread();
    }

    public static boolean isClient() {
        return !isServer();
    }
}
