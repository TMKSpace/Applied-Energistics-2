import io
import os

ROOT = r"D:\Code\java\ae2-fabric-probe\compat\java\net\neoforged\neoforge"

def patch(rel, pairs, required=True):
    path = os.path.join(ROOT, rel)
    with io.open(path, encoding="utf-8") as fh:
        text = fh.read()
    orig = text
    for old, new in pairs:
        if old not in text:
            if required:
                print("!! NOT FOUND in %s: %r" % (rel, old[:70]))
            continue
        text = text.replace(old, new)
    if text != orig:
        with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(text)
        print("patched", rel)

# SortedReloadListenerEvent: список ванильных слушателей может быть пустым
patch("event/SortedReloadListenerEvent.java", [(
    "        this.lastVanilla = vanillaListeners.getLast();",
    "        // nfcompat: список может прийти пустым (у клиента ванильных слушателей мы не видим).\\n"
    "        this.lastVanilla = vanillaListeners.isEmpty() ? null : vanillaListeners.getLast();"
)])

# LivingEvent: CommonHooks — только javadoc
patch("event/entity/living/LivingEvent.java", [(
    "import net.neoforged.neoforge.common.CommonHooks;\n", ""
)])

# FinalizeSpawnEvent: NeoForgeEventHandler — только javadoc; нужен свой флаг отмены спавна
patch("event/entity/living/FinalizeSpawnEvent.java", [
    ("import net.neoforged.neoforge.common.NeoForgeEventHandler;\n", ""),
    ("public class FinalizeSpawnEvent extends MobSpawnEvent implements ICancellableEvent {",
     "public class FinalizeSpawnEvent extends MobSpawnEvent implements ICancellableEvent {\n"
     "    // nfcompat: у Mob нет setSpawnCancelled — в NeoForge это патч. Держим флаг у себя.\n"
     "    private boolean spawnCancelled;"),
])

# EntityRenderersEvent: ClientHooks теперь наш
print("done")
