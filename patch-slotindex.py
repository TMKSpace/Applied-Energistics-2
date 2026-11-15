# -*- coding: utf-8 -*-
"""AppEngSlot: getSlotIndex() — метод, который NeoForge вписал в ванильный Slot."""
import io

PATH = r"D:\Code\java\ae2-fabric-probe\src\main\java\appeng\menu\slot\AppEngSlot.java"

OLD = """    public AppEngSlot(InternalInventory inv, int invSlot) {
        super(EMPTY_INVENTORY, invSlot, 0, 0);"""

NEW = """//#if FABRIC
    /**
     * NeoForge патчит ванильный {@link Slot}, добавляя {@code getSlotIndex()}.
     * В ванили индекс лежит в приватном поле {@code slot}, куда нам не достать,
     * поэтому отдаём собственный {@code invSlot}: он передаётся в {@code super(...)}
     * и всегда равен ему.
     */
    public int getSlotIndex() {
        return this.invSlot;
    }
//#endif

    public AppEngSlot(InternalInventory inv, int invSlot) {
        super(EMPTY_INVENTORY, invSlot, 0, 0);"""

with io.open(PATH, encoding="utf-8") as fh:
    text = fh.read()
if OLD not in text:
    raise SystemExit("не найдено")
with io.open(PATH, "w", encoding="utf-8", newline="\n") as fh:
    fh.write(text.replace(OLD, NEW, 1))
print("AppEngSlot: getSlotIndex добавлен")
