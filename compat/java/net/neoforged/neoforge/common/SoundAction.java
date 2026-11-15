/*
 * Часть nfcompat. В NeoForge SoundAction — запись в собственном реестре звуковых действий
 * (чки бака, испарение и т.п.). AE2 трогает только константы из SoundActions, поэтому
 * здесь лёгкий реестр по имени: полноценный реестр с кодеком появится, если понадобится.
 */
package net.neoforged.neoforge.common;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SoundAction {
    private static final Map<String, SoundAction> REGISTRY = new ConcurrentHashMap<>();

    private final String name;

    private SoundAction(String name) {
        this.name = name;
    }

    public static SoundAction get(String name) {
        return REGISTRY.computeIfAbsent(name, SoundAction::new);
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "SoundAction[" + name + "]";
    }
}
