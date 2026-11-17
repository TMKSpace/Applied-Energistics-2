/*
 * Часть nfcompat. Доступ к письменному реестру задачи загрузки.
 *
 * `RegistryLoadTask#registry` — private final WritableRegistry. Ровно его нужно отдать
 * в `RegisterGameTestsEvent`, потому что после `freeze` регистрировать уже нельзя.
 * Fabric'овский `fabric-gametest-api-v1` делает то же самое через свой classtweaker;
 * нам, чтобы не трогать чужой accesswidener (src/main/resources/ae2.accesswidener
 * правит другой лейн), проще аксессор-миксин.
 */
package nfcompat.gametest.mixin;

import net.minecraft.core.WritableRegistry;
import net.minecraft.resources.RegistryLoadTask;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RegistryLoadTask.class)
public interface RegistryLoadTaskAccessor {

    @Accessor("registry")
    WritableRegistry<?> nfcompat$registry();
}
