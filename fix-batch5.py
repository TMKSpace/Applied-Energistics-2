# -*- coding: utf-8 -*-
"""Починка двух сломанных мест после предыдущего батча."""
import io

def fix(path, old, new, label):
    with io.open(path, encoding="utf-8") as fh:
        text = fh.read()
    if old not in text:
        print("!! не найдено:", label)
        return
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text.replace(old, new, 1))
    print("починено:", label)

# A. EntropyManipulatorCategory: двойная вложенность + потерянная скобка
fix(r"D:\Code\java\ae2-fabric-probe\src\client\java\appeng\client\integrations\jei\EntropyManipulatorCategory.java",
"""                            //#if FABRIC
                            //#if FABRIC
Either.left(ItemModText.FLOWING_FLUID_NAME.text(
                net.neoforged.neoforge.fluids.FluidType.of(fluid).getDescription()));
//#else
Either.left(ItemModText.FLOWING_FLUID_NAME.text(fluid.getFluidType().getDescription(fluid))));
//#endif
                            //#else
                            Either.left(ItemModText.FLOWING_FLUID_NAME.text(fluid.getFluidType().getDescription())));
                            //#endif""",
"""//#if FABRIC
                            Either.left(ItemModText.FLOWING_FLUID_NAME.text(
                                    net.neoforged.neoforge.fluids.FluidType.of(fluid).getDescription())));
//#else
                            Either.left(ItemModText.FLOWING_FLUID_NAME.text(fluid.getFluidType().getDescription())));
//#endif""",
"EntropyManipulatorCategory")

# B. MenuTypeBuilder: маркер на весь оператор, без трюка с if(false)
fix(r"D:\Code\java\ae2-fabric-probe\src\main\java\appeng\menu\implementations\MenuTypeBuilder.java",
"""        //#if FABRIC
player.openMenu(new AppEngMenuProvider()); if (false) { java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> unused = buffer -> {
//#else
player.openMenu(new AppEngMenuProvider(), buffer -> {
//#endif
            MenuLocators.writeToPacket(buffer, locator);
            buffer.writeBoolean(fromSubMenu);

            if (initialDataSerializer != null) {
                initialDataSerializer.serializeInitialData(accessInterface, buffer);
            }
        });""",
"""//#if FABRIC
        // nfcompat: ванильный openMenu не умеет передавать доп. данные, поэтому меню
        // откроется без locator/initialData. TODO: ExtendedScreenHandlerType.
        player.openMenu(new AppEngMenuProvider());
//#else
        player.openMenu(new AppEngMenuProvider(), buffer -> {
            MenuLocators.writeToPacket(buffer, locator);
            buffer.writeBoolean(fromSubMenu);

            if (initialDataSerializer != null) {
                initialDataSerializer.serializeInitialData(accessInterface, buffer);
            }
        });
//#endif""",
"MenuTypeBuilder")
