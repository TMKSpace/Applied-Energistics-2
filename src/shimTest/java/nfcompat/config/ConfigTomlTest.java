/*
 * Тест конфиг-слоя шима: настоящий TOML на диске.
 *
 * Что держим:
 *  - значения из файла доходят до `ConfigValue#get()` (раньше правки игрока игнорировались);
 *  - `ConfigValue#set` + `save()` пишут файл, `ModConfig#save()` тоже;
 *  - отсутствующий файл создаётся с дефолтами по спецификации;
 *  - повторное чтение (`reloadConfigs`) подхватывает правки и постит `Reloading`;
 *  - `Loading` приходит после того, как мод дособран (AE2 вешает слушателя уже после
 *    `registerConfig`, внутри своего конструктора);
 *  - `validateSpec` по-прежнему режет restart-типы, недопустимые для вида конфига.
 *
 * Каталог конфигов подменяем на `@TempDir`: без этого шим уходит в общий
 * `java.io.tmpdir/nfcompat-test-config` и тесты начали бы видеть файлы друг друга.
 */
package nfcompat.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlFormat;

import net.neoforged.bus.api.SimpleEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import net.neoforged.neoforge.common.ModConfigSpec;

class ConfigTomlTest {

    private static final String FILE = "test-client.toml";

    @TempDir
    Path configDir;

    private final SimpleEventBus bus = new SimpleEventBus();
    private final List<String> events = new ArrayList<>();

    @BeforeEach
    void useTempConfigDir() {
        ModContainer.setConfigDirOverride(configDir);
    }

    @AfterEach
    void restoreConfigDir() {
        ModContainer.setConfigDirOverride(null);
    }

    @Test
    @DisplayName("значения из существующего TOML попадают в ConfigValue#get()")
    void readsValuesFromFile() throws IOException {
        var spec = buildSpec();
        Files.writeString(file(), """
                [section]
                count = 42
                name = "from file"
                """);

        var container = container();
        listen();
        container.registerConfig(ModConfig.Type.CLIENT, spec.spec(), FILE);

        assertThat(spec.count().get()).isEqualTo(42);
        assertThat(spec.name().get()).isEqualTo("from file");
        assertThat(events).isEmpty();

        // Так это делает NfCompat.runRegistrationPhases уже после сборки всех модов.
        ModContainer.postAllPendingConfigLoads();
        assertThat(events).containsExactly("Loading:" + FILE);
    }

    @Test
    @DisplayName("Loading приходит после конструктора мода, а не внутри registerConfig")
    void loadingIsPostedAfterModConstruction() {
        // Ровно повторяем AEConfig: регистрация в первой строке конструктора,
        // слушатель — ниже. Раньше событие уходило мимо AE2, и common.sync() не звался.
        var spec = buildSpec();
        var container = container();
        container.registerConfig(ModConfig.Type.CLIENT, spec.spec(), FILE);

        listen();
        assertThat(events).isEmpty();

        assertThat(container.postPendingConfigLoads()).isEqualTo(1);
        assertThat(events).containsExactly("Loading:" + FILE);

        // Повторный вызов ничего не дублирует.
        assertThat(container.postPendingConfigLoads()).isZero();
        assertThat(events).containsExactly("Loading:" + FILE);
    }

    @Test
    @DisplayName("set() + save() пишет файл; ModConfig#save() тоже")
    void writesFileOnSetAndSave() throws IOException {
        var spec = buildSpec();
        var container = container();
        container.registerConfig(ModConfig.Type.CLIENT, spec.spec(), FILE);

        spec.count().set(7);
        spec.count().save();
        assertThat(readToml().getInt("section.count")).isEqualTo(7);

        // Второй путь: ModConfig#save() (его дёргает, например, экран конфигов).
        spec.name().set("edited");
        container.getConfigs().get(0).save();
        assertThat((String) readToml().get("section.name")).isEqualTo("edited");
    }

    @Test
    @DisplayName("отсутствующий файл создаётся с дефолтами по спецификации")
    void createsMissingFileWithDefaults() throws IOException {
        var spec = buildSpec();

        container().registerConfig(ModConfig.Type.CLIENT, spec.spec(), FILE);

        assertThat(Files.exists(file())).isTrue();
        assertThat(readToml().getInt("section.count")).isEqualTo(1);
        assertThat((String) readToml().get("section.name")).isEqualTo("default");
        assertThat(spec.count().get()).isEqualTo(1);
    }

    @Test
    @DisplayName("вышедшее за range значение правится и записывается обратно")
    void correctsOutOfRangeValue() throws IOException {
        var spec = buildSpec();
        Files.writeString(file(), """
                [section]
                count = 5000
                name = "kept"
                """);

        container().registerConfig(ModConfig.Type.CLIENT, spec.spec(), FILE);

        // Range.correct() не сбрасывает в дефолт, а прижимает к границе.
        assertThat(spec.count().get()).isEqualTo(1000);
        assertThat(readToml().getInt("section.count")).isEqualTo(1000);
    }

    @Test
    @DisplayName("reloadConfigs() перечитывает файл и постит Reloading")
    void reloadPicksUpEditsAndPostsReloading() throws IOException {
        var spec = buildSpec();
        Files.writeString(file(), """
                [section]
                count = 5
                name = "first"
                """);
        var container = container();
        listen();
        container.registerConfig(ModConfig.Type.CLIENT, spec.spec(), FILE);
        assertThat(spec.count().get()).isEqualTo(5);
        // События ещё не было: его пошлёт конец сборки мода (или сам reloadConfigs).
        assertThat(events).isEmpty();

        Files.writeString(file(), """
                [section]
                count = 9
                name = "second"
                """);

        assertThat(container.reloadConfigs()).isEqualTo(1);

        assertThat(spec.count().get()).isEqualTo(9);
        assertThat(spec.name().get()).isEqualTo("second");
        assertThat(events).containsExactly("Loading:" + FILE, "Reloading:" + FILE);
    }

    @Test
    @DisplayName("validateSpec всё ещё режет gameRestart в SYNCED-конфиге")
    void validateSpecRejectsGameRestartInSynced() {
        var builder = new ModConfigSpec.Builder();
        builder.push("section");
        builder.gameRestart().define("value", 1);
        builder.pop();
        var spec = builder.build();

        assertThatThrownBy(() -> container().registerConfig(ModConfig.Type.SYNCED, spec, FILE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("restart of type GAME");

        // До чтения файла дело не дошло: спека не прошла валидацию.
        assertThat(Files.exists(file())).isFalse();
    }

    private ModContainer container() {
        return new ModContainer(new ModInfo("test", "Test", "1.0"), bus);
    }

    /** Слушатели конфиг-событий: у AE2 они висят на шине мода. */
    private void listen() {
        bus.addListener(ModConfigEvent.Loading.class, e -> events.add("Loading:" + e.getConfig().getFileName()));
        bus.addListener(ModConfigEvent.Reloading.class, e -> events.add("Reloading:" + e.getConfig().getFileName()));
    }

    /** Спека как у мода: секция с числом и строкой — хватает, чтобы поймать и чтение, и запись. */
    private record Spec(ModConfigSpec spec, ModConfigSpec.IntValue count, ModConfigSpec.ConfigValue<String> name) {
    }

    private static Spec buildSpec() {
        var builder = new ModConfigSpec.Builder();
        builder.push("section");
        var count = builder.defineInRange("count", 1, 0, 1000);
        var name = builder.define("name", "default");
        builder.pop();
        return new Spec(builder.build(), count, name);
    }

    private Path file() {
        return configDir.resolve(FILE);
    }

    private Config readToml() {
        try {
            return TomlFormat.instance().createParser().parse(Files.readString(file()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
