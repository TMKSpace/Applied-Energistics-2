/*
 * Часть nfcompat. Контракт «спецификации» конфига.
 *
 * В NeoForge объявлен в загрузчике (FancyModLoader). Здесь переписан по тому, что
 * реально вызывает вендоренный ModConfigSpec.
 */
package net.neoforged.fml.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableCommentedConfig;
import org.jetbrains.annotations.Nullable;

public interface IConfigSpec {
    /** {@return true, если в спецификации нет ни одного значения} */
    boolean isEmpty();

    /** Проверяет, что спецификация допустима для данного типа конфига. */
    void validateSpec(ModConfig config);

    /** {@return true, если переданный конфиг соответствует спецификации} */
    boolean isCorrect(UnmodifiableCommentedConfig config);

    /** Правит переданный конфиг по спецификации. */
    void correct(CommentedConfig config);

    /** Привязывает загруженный конфиг к спецификации (или отвязывает, если null). */
    void acceptConfig(@Nullable ILoadedConfig config);

    /**
     * Загруженный на диск конфиг.
     */
    interface ILoadedConfig {
        CommentedConfig config();

        void save();
    }
}
