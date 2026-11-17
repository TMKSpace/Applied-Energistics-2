/*
 * Часть nfcompat. Межмодовые сообщения.
 *
 * В NeoForge это шина обмена сообщениями между модами на этапе загрузки. На Fabric
 * прямого аналога нет; сообщения просто никуда не уходят, но факт отправки сохраняем.
 */
package net.neoforged.fml;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class InterModComms {
    public record Message(String senderModId, String modId, String key, Supplier<?> value) {
    }

    private static final List<Message> SENT = new ArrayList<>();

    private InterModComms() {
    }

    public static boolean sendTo(String modId, String key, Supplier<?> value) {
        SENT.add(new Message("appliedenergistics2", modId, key, value));
        return true;
    }
}
