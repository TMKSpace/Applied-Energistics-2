package nfcompat.tags;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.common.Tags;

/**
 * Convention-теги NeoForge. Ошибка здесь не падает и не логируется: тег просто
 * указывает в пустоту, и рецепт "любой пылью" перестаёт работать. Поэтому
 * проверяем namespace, путь и реестр у каждой константы.
 * <p>
 * Бутстрап игры не нужен: {@link Registries} — это статические ResourceKey,
 * а TagKey.create() ничего в реестры не пишет (в отличие от BuiltInRegistries).
 */
class TagsTest {

    @Test
    @DisplayName("Items.DUSTS: namespace c, путь dusts, реестр ITEM")
    void itemDusts() {
        TagKey<?> tag = Tags.Items.DUSTS;

        assertThat(tag.location().getNamespace()).isEqualTo("c");
        assertThat(tag.location().getPath()).isEqualTo("dusts");
        assertThat(tag.registry()).isEqualTo(Registries.ITEM);
        assertThat(tag.isFor(Registries.ITEM)).isTrue();
    }

    @Test
    @DisplayName("вложенные пути не схлопываются: gems/quartz и chests/wooden")
    void nestedPaths() {
        assertThat(Tags.Items.GEMS_QUARTZ.location().toString()).isEqualTo("c:gems/quartz");
        assertThat(Tags.Items.CHESTS_WOODEN.location().toString()).isEqualTo("c:chests/wooden");
        assertThat(Tags.Items.DUSTS_GLOWSTONE.location().toString()).isEqualTo("c:dusts/glowstone");
        assertThat(Tags.Items.GLASS_BLOCKS_CHEAP.location().toString()).isEqualTo("c:glass_blocks/cheap");
        assertThat(Tags.Items.INGOTS_IRON.location().toString()).isEqualTo("c:ingots/iron");
    }

    @Test
    @DisplayName("Blocks: тот же путь, но реестр BLOCK")
    void blocksRegistry() {
        TagKey<?> tag = Tags.Blocks.STONES;

        assertThat(tag.location().toString()).isEqualTo("c:stones");
        assertThat(tag.registry()).isEqualTo(Registries.BLOCK);
        assertThat(tag.isFor(Registries.BLOCK)).isTrue();
        assertThat(tag.isFor(Registries.ITEM)).isFalse();
    }

    @Test
    @DisplayName("Biomes: реестр BIOME")
    void biomesRegistry() {
        TagKey<?> tag = Tags.Biomes.IS_COLD;

        assertThat(tag.location().toString()).isEqualTo("c:is_cold");
        assertThat(tag.registry()).isEqualTo(Registries.BIOME);
        assertThat(tag.isFor(Registries.BIOME)).isTrue();
    }

    @Test
    @DisplayName("одинаковый путь в разных реестрах — разные теги")
    void samePathDifferentRegistry() {
        assertThat(Tags.Items.STONES).isNotEqualTo(Tags.Blocks.STONES);
        assertThat(Tags.Items.CHESTS.location()).isEqualTo(Tags.Blocks.CHESTS.location());
        assertThat(Tags.Items.BUDDING_BLOCKS).isNotEqualTo(Tags.Blocks.BUDDING_BLOCKS);
    }

    @Test
    @DisplayName("разные пути в одном реестре — разные теги")
    void differentPathsDifferentTags() {
        assertThat(Tags.Items.DUSTS).isNotEqualTo(Tags.Items.DUSTS_GLOWSTONE);
        assertThat(Tags.Items.GEMS).isNotEqualTo(Tags.Items.GEMS_QUARTZ);
    }

    @Test
    @DisplayName("созданный с нуля TagKey с тем же адресом равен константе шима")
    void equalToManuallyCreatedTag() {
        TagKey<?> manual = TagKey.create(Registries.ITEM,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("c", "dusts"));

        assertThat(manual).isEqualTo(Tags.Items.DUSTS);
        assertThat(manual.hashCode()).isEqualTo(Tags.Items.DUSTS.hashCode());
    }

    @Test
    @DisplayName("все константы Tags: namespace c, непустой путь, правильный реестр")
    void sweepAllConstants() throws Exception {
        List<String> problems = new ArrayList<>();

        checkNested(Tags.Items.class, Registries.ITEM, problems);
        checkNested(Tags.Blocks.class, Registries.BLOCK, problems);
        checkNested(Tags.Biomes.class, Registries.BIOME, problems);

        assertThat(problems).as("битые константы тегов").isEmpty();
    }

    private static void checkNested(Class<?> holder, ResourceKey<?> expectedRegistry, List<String> problems)
            throws Exception {
        int count = 0;
        for (Field field : holder.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != TagKey.class) {
                continue;
            }
            count++;
            TagKey<?> tag = (TagKey<?>) field.get(null);
            String name = holder.getSimpleName() + "." + field.getName();

            if (tag == null) {
                problems.add(name + ": null");
                continue;
            }
            if (!"c".equals(tag.location().getNamespace())) {
                problems.add(name + ": namespace " + tag.location().getNamespace());
            }
            if (tag.location().getPath().isEmpty()) {
                problems.add(name + ": пустой путь");
            }
            if (!expectedRegistry.equals(tag.registry())) {
                problems.add(name + ": реестр " + tag.registry() + " вместо " + expectedRegistry);
            }
        }

        if (count == 0) {
            problems.add(holder.getSimpleName() + ": ни одной константы (переименовали?)");
        }
    }
}
