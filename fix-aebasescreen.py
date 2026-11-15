# -*- coding: utf-8 -*-
"""Ручная починка трёх мест в AEBaseScreen (маркеры попали внутрь выражений)."""
import io

PATH = r"D:\Code\java\ae2-fabric-probe\src\client\java\appeng\client\gui\AEBaseScreen.java"

FIXES = [
(
"""            if (//#if FABRIC
            configInv.isItemValid(nfcompat.menu.SlotIndices.get(slot), wrappedStack)
            //#else
            configInv.isItemValid(slot.getSlotIndex(), wrappedStack)
            //#endif) {""",
"""//#if FABRIC
            if (configInv.isItemValid(nfcompat.menu.SlotIndices.get(slot), wrappedStack)) {
//#else
            if (configInv.isItemValid(slot.getSlotIndex(), wrappedStack)) {
//#endif"""
),
(
"""                        //#if FABRIC
                        if (nfcompat.menu.SlotIndices.get(s) == j && s.container == this.menu.getPlayerInventory()
                        //#else
                        //#if FABRIC
                        if (nfcompat.menu.SlotIndices.get(s) == j
                        //#else
                        if (s.getSlotIndex() == j
                        //#endif && s.container == this.menu.getPlayerInventory()
                        //#endif
                                && !s.mayPickup(this.menu.getPlayerInventory().player)) {""",
"""//#if FABRIC
                        if (nfcompat.menu.SlotIndices.get(s) == j && s.container == this.menu.getPlayerInventory()
                                && !s.mayPickup(this.menu.getPlayerInventory().player)) {
//#else
                        if (s.getSlotIndex() == j && s.container == this.menu.getPlayerInventory()
                                && !s.mayPickup(this.menu.getPlayerInventory().player)) {
//#endif"""
),
(
"""                            if (s.getSlotIndex() == j
                                    && s.container == this.menu.getPlayerInventory()) {""",
"""//#if FABRIC
                            if (nfcompat.menu.SlotIndices.get(s) == j
//#else
                            if (s.getSlotIndex() == j
//#endif
                                    && s.container == this.menu.getPlayerInventory()) {"""
),
]

with io.open(PATH, encoding="utf-8") as fh:
    text = fh.read()

for i, (old, new) in enumerate(FIXES, 1):
    if old not in text:
        print("!! не найдено #%d" % i)
        continue
    text = text.replace(old, new, 1)
    print("починено #%d" % i)

with io.open(PATH, "w", encoding="utf-8", newline="\n") as fh:
    fh.write(text)
