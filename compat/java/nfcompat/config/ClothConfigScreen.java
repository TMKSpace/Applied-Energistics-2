/*
 * Часть nfcompat. Экран настроек AE2 на Cloth Config.
 *
 * ЗАЧЕМ. Наши конфиги — вендоренный неофорджевский `ModConfigSpec` (TOML через night-config),
 * а у Fabric-игрока настройки мода открываются из Mod Menu. Cloth Config ничего не «подхватывает»
 * сам, поэтому здесь мы проходим по спеке и строим экран руками: по одной записи на
 * `ConfigValue`, значение по умолчанию из спеки, сохранение через `set` + `save` конфига.
 *
 * ЧТО ПОДДЕРЖИВАЕМ. Boolean, Integer/Long (слайдер при наличии range, иначе поле), Double/Float,
 * enum, String, List<String>. Этого хватает всем настройкам AE2; если апстрим добавит другой тип,
 * значение просто не попадёт на экран (в лог уходит warn), а не уронит открытие.
 *
 * ЧЕГО НЕТ. `@Config`/AutoConfig из Cloth не применим: он работает со своими аннотированными
 * POJO, а у нас уже есть готовая `ModConfigSpec`. Поэтому — ручной обход.
 */
package nfcompat.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;

import nfcompat.NfCompat;

public final class ClothConfigScreen {

    private static final Logger LOGGER = LoggerFactory.getLogger("nfcompat/config");

    private ClothConfigScreen() {
    }

    public static Screen create(Screen parent) {
        var builder = me.shedaniel.clothconfig2.api.ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Applied Energistics 2"));

        var entries = builder.entryBuilder();

        for (var config : NfCompat.container().getConfigs()) {
            if (!(config.getSpec() instanceof ModConfigSpec spec)) {
                continue;
            }
            var category = builder.getOrCreateCategory(title(config));
            addValues(category, entries, spec.getValues());
        }

        // Записи сами применяют значения через setSaveConsumer, здесь только пишем TOML на диск.
        builder.setSavingRunnable(() -> {
            for (var config : NfCompat.container().getConfigs()) {
                config.save();
            }
        });

        return builder.build();
    }

    /**
     * AE2 описывает конфиги секциями (`Builder.push("...")`), поэтому значения лежат не на
     * верхнем уровне, а внутри вложенных {@code UnmodifiableConfig}. Обходим рекурсивно:
     * листья — это {@code ConfigValue}, всё остальное — секции.
     */
    private static void addValues(ConfigCategory category, ConfigEntryBuilder entries,
            com.electronwill.nightconfig.core.UnmodifiableConfig section) {
        for (var value : section.valueMap().values()) {
            if (value instanceof com.electronwill.nightconfig.core.UnmodifiableConfig nested) {
                addValues(category, entries, nested);
            } else if (value instanceof ModConfigSpec.ConfigValue<?> configValue) {
                addEntry(category, entries, configValue);
            }
        }
    }

    private static Component title(ModConfig config) {
        var file = config.getFileName();
        return Component.literal(switch (config.getType()) {
            case COMMON -> "Common (" + file + ")";
            case CLIENT -> "Client (" + file + ")";
            case SERVER -> "Server (" + file + ")";
            default -> file;
        });
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void addEntry(ConfigCategory category, ConfigEntryBuilder entries,
            ModConfigSpec.ConfigValue<?> value) {
        var label = Component.literal(String.join(" → ", value.getPath()));
        var comment = value.getSpec().getComment();
        var tooltip = comment != null ? new Component[] { Component.literal(comment) } : new Component[0];
        var current = value.get();
        var range = value.getSpec().getRange();

        try {
            if (current instanceof Boolean b) {
                var entry = entries.startBooleanToggle(label, b)
                        .setDefaultValue(() -> (Boolean) value.getDefault())
                        .setTooltip(tooltip)
                        .setSaveConsumer(v -> ((ModConfigSpec.ConfigValue<Boolean>) value).set(v))
                        .build();
                category.addEntry(entry);
                return;
            }
            if (current instanceof Integer i) {
                if (range != null && range.getMin() instanceof Integer min && range.getMax() instanceof Integer max) {
                    var entry = entries.startIntSlider(label, i, min, max)
                            .setDefaultValue(() -> (Integer) value.getDefault())
                            .setTooltip(tooltip)
                            .setSaveConsumer(v -> ((ModConfigSpec.ConfigValue<Integer>) value).set(v))
                            .build();
                    category.addEntry(entry);
                } else {
                    var entry = entries.startIntField(label, i)
                            .setDefaultValue(() -> (Integer) value.getDefault())
                            .setTooltip(tooltip)
                            .setSaveConsumer(v -> ((ModConfigSpec.ConfigValue<Integer>) value).set(v))
                            .build();
                    category.addEntry(entry);
                }
                return;
            }
            if (current instanceof Long l) {
                if (range != null && range.getMin() instanceof Long min && range.getMax() instanceof Long max) {
                    var entry = entries.startLongSlider(label, l, min, max)
                            .setDefaultValue(() -> (Long) value.getDefault())
                            .setTooltip(tooltip)
                            .setSaveConsumer(v -> ((ModConfigSpec.ConfigValue<Long>) value).set(v))
                            .build();
                    category.addEntry(entry);
                } else {
                    var entry = entries.startStrField(label, String.valueOf(l))
                            .setDefaultValue(() -> String.valueOf(value.getDefault()))
                            .setTooltip(tooltip)
                            .setSaveConsumer(v -> {
                                try {
                                    ((ModConfigSpec.ConfigValue<Long>) value).set(Long.parseLong(v.trim()));
                                } catch (NumberFormatException ignored) {
                                }
                            })
                            .build();
                    category.addEntry(entry);
                }
                return;
            }
            if (current instanceof Double d) {
                var entry = entries.startDoubleField(label, d)
                        .setDefaultValue(() -> (Double) value.getDefault())
                        .setTooltip(tooltip)
                        .setSaveConsumer(v -> ((ModConfigSpec.ConfigValue<Double>) value).set(v))
                        .build();
                category.addEntry(entry);
                return;
            }
            if (current instanceof Float f) {
                var entry = entries.startFloatField(label, f)
                        .setDefaultValue(() -> (Float) value.getDefault())
                        .setTooltip(tooltip)
                        .setSaveConsumer(v -> ((ModConfigSpec.ConfigValue<Float>) value).set(v))
                        .build();
                category.addEntry(entry);
                return;
            }
            if (current instanceof Enum<?> e) {
                var entry = entries.startEnumSelector(label, (Class) e.getDeclaringClass(), e)
                        .setDefaultValue(() -> (Enum) value.getDefault())
                        .setTooltip(tooltip)
                        .setSaveConsumer(v -> ((ModConfigSpec.ConfigValue) value).set(v))
                        .build();
                category.addEntry(entry);
                return;
            }
            if (current instanceof String s) {
                var entry = entries.startStrField(label, s)
                        .setDefaultValue(() -> (String) value.getDefault())
                        .setTooltip(tooltip)
                        .setSaveConsumer(v -> ((ModConfigSpec.ConfigValue<String>) value).set(v))
                        .build();
                category.addEntry(entry);
                return;
            }
            if (current instanceof List<?> list) {
                var entry = entries.startStrList(label, (List<String>) list)
                        .setDefaultValue(() -> (List<String>) value.getDefault())
                        .setTooltip(tooltip)
                        .setSaveConsumer(v -> ((ModConfigSpec.ConfigValue<List<String>>) value).set(new ArrayList<>(v)))
                        .build();
                category.addEntry(entry);
                return;
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Cannot build config entry for {}: {}", label.getString(), e.toString());
            return;
        }

        LOGGER.warn("Unsupported config value type for {}: {}", label.getString(),
                current == null ? "null" : current.getClass().getName());
    }
}
