/*
 * Часть nfcompat. Picture-in-picture рендереры (`RegisterPictureInPictureRenderersEvent`).
 *
 * NeoForge патчит конструктор `GameRenderer`: он рассылает событие, собирает список
 * рендереров и отдаёт его в `GuiRenderer`. `GuiRenderer` сам раскладывает их по карте
 * "класс состояния -> рендерер" (через `PictureInPictureRenderer#getRenderStateClass`),
 * поэтому попасть в неё можно только на этапе конструктора.
 *
 * Мы делаем то же самое: копим регистрации в списке, а миксин
 * `nfcompat.mixin.client.GameRendererMixin` подкладывает их третьим аргументом
 * вызова `new GuiRenderer(...)`.
 *
 * Важно: это только регистрация. Сами состояния PiP в 26.3 отправляются через
 * `GuiRenderState#addPicturesInPictureState`, а у AE2 вызов (`FluidBlockRendering`)
 * на Fabric пока заглушен — см. комментарий там.
 */
package nfcompat.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.gui.PictureInPictureRendererRegistration;
import net.neoforged.neoforge.common.NeoForge;

public final class PictureInPicture {

    private static final List<PictureInPictureRendererRegistration<?>> REGISTRATIONS = new ArrayList<>();

    private static boolean posted;

    private PictureInPicture() {
    }

    /**
     * Рассылает событие ровно один раз. Миксин дергает это на случай, если
     * `GameRenderer` соберётся раньше клиентской инициализации мода.
     */
    public static void install() {
        if (posted) {
            return;
        }
        posted = true;
        NeoForge.EVENT_BUS.post(new RegisterPictureInPictureRenderersEvent(REGISTRATIONS));
        if (!REGISTRATIONS.isEmpty()) {
            System.out.println("[nfcompat] picture-in-picture рендереров: " + REGISTRATIONS.size());
        }
    }

    /** {@return по экземпляру каждого зарегистрированного рендерера} */
    public static List<PictureInPictureRenderer<?>> createAll() {
        install();
        List<PictureInPictureRenderer<?>> renderers = new ArrayList<>(REGISTRATIONS.size());
        for (var registration : REGISTRATIONS) {
            renderers.add(registration.factory().get());
        }
        return renderers;
    }
}
