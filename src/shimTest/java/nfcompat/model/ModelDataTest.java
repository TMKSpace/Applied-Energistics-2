package nfcompat.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.model.data.ModelProperty;

/**
 * ModelData — вендоренный из NeoForge контейнер данных модели (LGPL). Своей логики
 * в нём нет, но именно поэтому он интересен: шим легко может "поправить" вендоренный
 * файл и тихо сломать контракт, на который рассчитывают модели блоков AE2.
 * Тест держит публичный контракт и одну внутреннюю оптимизацию (array-map -> hash-map).
 */
class ModelDataTest {

    static final ModelProperty<Integer> COUNT = new ModelProperty<>();
    static final ModelProperty<String> NAME = new ModelProperty<>();
    static final ModelProperty<Boolean> FLAG = new ModelProperty<>();
    static final ModelProperty<Long> TIME = new ModelProperty<>();
    static final ModelProperty<Double> RATE = new ModelProperty<>();

    @Test
    @DisplayName("пустой билдер отдаёт тот же самый ModelData.EMPTY")
    void emptyBuilderReturnsSharedEmpty() {
        assertThat(ModelData.builder().build()).isSameAs(ModelData.EMPTY);
        assertThat(ModelData.EMPTY.getProperties()).isEmpty();
    }

    @Test
    @DisplayName("derive() от пустого тоже даёт EMPTY, а не новый объект")
    void deriveOfEmptyStaysEmpty() {
        assertThat(ModelData.EMPTY.derive().build()).isSameAs(ModelData.EMPTY);
    }

    @Test
    @DisplayName("with() + build(): get/has отвечают по своим ключам")
    void builderStoresValues() {
        var data = ModelData.builder()
                .with(COUNT, 7)
                .with(NAME, "ae2")
                .build();

        assertThat(data.has(COUNT)).isTrue();
        assertThat(data.get(COUNT)).isEqualTo(7);
        assertThat(data.has(NAME)).isTrue();
        assertThat(data.get(NAME)).isEqualTo("ae2");
        assertThat(data.getProperties()).containsExactlyInAnyOrder(COUNT, NAME);
    }

    @Test
    @DisplayName("чужое свойство: has=false, get=null (а не исключение)")
    void missingProperty() {
        var data = ModelData.of(COUNT, 1);

        assertThat(data.has(NAME)).isFalse();
        assertThat(data.get(NAME)).isNull();
    }

    @Test
    @DisplayName("повторный with() по тому же свойству заменяет значение, размер не растёт")
    void duplicateWithReplacesValue() {
        var data = ModelData.builder()
                .with(COUNT, 1)
                .with(COUNT, 2)
                .build();

        assertThat(data.get(COUNT)).isEqualTo(2);
        assertThat(data.getProperties()).hasSize(1);
    }

    @Test
    @DisplayName("валидатор свойства отвергает плохое значение: IllegalStateException из with()")
    void validatorRejectsBadValueInWith() {
        var positive = new ModelProperty<Integer>(value -> value != null && value > 0);

        assertThatThrownBy(() -> ModelData.builder().with(positive, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid");
        assertThatThrownBy(() -> ModelData.builder().with(positive, -5))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("валидатор работает и в ModelData.of()")
    void validatorRejectsBadValueInOf() {
        var positive = new ModelProperty<Integer>(value -> value != null && value > 0);

        assertThatThrownBy(() -> ModelData.of(positive, -1))
                .isInstanceOf(IllegalStateException.class);
        assertThat(ModelData.of(positive, 1).get(positive)).isEqualTo(1);
    }

    @Test
    @DisplayName("дефолтное свойство null пропускает: has=true, а get=null")
    void defaultPropertyAcceptsNull() {
        // Вендоренный ModelProperty() использует Predicates.alwaysTrue(), то есть
        // null не проверяется вообще. Полезно помнить: has() не означает "значение есть".
        var data = ModelData.of(COUNT, null);

        assertThat(data.has(COUNT)).isTrue();
        assertThat(data.get(COUNT)).isNull();
        assertThat(data.getProperties()).containsExactly(COUNT);
    }

    @Test
    @DisplayName("of() без билдера: одно свойство, тот же контракт")
    void ofSingleProperty() {
        var data = ModelData.of(TIME, 1234L);

        assertThat(data.get(TIME)).isEqualTo(1234L);
        assertThat(data.getProperties()).hasSize(1);
        assertThat(data.has(COUNT)).isFalse();
    }

    @Test
    @DisplayName("derive() копирует данные и не меняет оригинал")
    void deriveCopies() {
        var original = ModelData.builder().with(COUNT, 1).with(NAME, "base").build();
        var derived = original.derive().with(FLAG, true).build();

        assertThat(derived.get(COUNT)).isEqualTo(1);
        assertThat(derived.get(NAME)).isEqualTo("base");
        assertThat(derived.get(FLAG)).isTrue();
        assertThat(derived).isNotSameAs(original);

        // оригинал не должен увидеть производное свойство
        assertThat(original.has(FLAG)).isFalse();
        assertThat(original.getProperties()).hasSize(2);
    }

    @Test
    @DisplayName("derive() с заменой значения не трогает оригинал")
    void deriveOverridesWithoutMutating() {
        var original = ModelData.of(COUNT, 1);
        var derived = original.derive().with(COUNT, 2).build();

        assertThat(derived.get(COUNT)).isEqualTo(2);
        assertThat(original.get(COUNT)).isEqualTo(1);
    }

    @Test
    @DisplayName("getProperties() только для чтения")
    void propertySetIsUnmodifiable() {
        var data = ModelData.of(COUNT, 1);

        assertThatThrownBy(() -> data.getProperties().remove(COUNT))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("после 4 свойств билдер переезжает на hash-map, поведение не меняется")
    void arrayMapConvertsToHashMapAfterFourProperties() throws Exception {
        var three = ModelData.builder().with(COUNT, 1).with(NAME, "a").with(FLAG, true).build();
        var four = three.derive().with(TIME, 9L).build();
        var five = four.derive().with(RATE, 1.5).build();

        // Оптимизация вендоренного Builder.HASH_THRESHOLD = 4: до порога array-map,
        // на пороге — Reference2ReferenceOpenHashMap (иначе был бы megamorphic Map.get).
        assertThat(mapClassOf(three)).isEqualTo("Reference2ReferenceArrayMap");
        assertThat(mapClassOf(four)).isEqualTo("Reference2ReferenceOpenHashMap");
        assertThat(mapClassOf(five)).isEqualTo("Reference2ReferenceOpenHashMap");

        // и главное: на данных это не отражается
        assertThat(three.getProperties()).hasSize(3);
        assertThat(four.getProperties()).hasSize(4);
        assertThat(five.getProperties()).hasSize(5);
        assertThat(five.get(COUNT)).isEqualTo(1);
        assertThat(five.get(NAME)).isEqualTo("a");
        assertThat(five.get(FLAG)).isTrue();
        assertThat(five.get(TIME)).isEqualTo(9L);
        assertThat(five.get(RATE)).isEqualTo(1.5);
    }

    @Test
    @DisplayName("derive() от 4+ свойств сразу берёт hash-map, от 3 — array-map")
    void derivePicksMapTypeBySize() throws Exception {
        var three = ModelData.builder().with(COUNT, 1).with(NAME, "a").with(FLAG, true).build();
        var four = three.derive().with(TIME, 9L).build();

        assertThat(mapClassOf(three.derive().build())).isEqualTo("Reference2ReferenceArrayMap");
        assertThat(mapClassOf(four.derive().build())).isEqualTo("Reference2ReferenceOpenHashMap");
    }

    @Test
    @DisplayName("свойства различаются по идентичности, а не по equals")
    void propertiesAreComparedByIdentity() {
        // ModelProperty не переопределяет equals/hashCode: два внешне одинаковых
        // свойства — разные ключи. Для моделей это важно (иначе данные перетирались бы).
        var other = new ModelProperty<Integer>();
        var data = ModelData.of(COUNT, 1);

        assertThat(data.has(other)).isFalse();
        assertThat(data.get(other)).isNull();
    }

    /** Внутренний Map контейнера: единственный способ увидеть оптимизацию array->hash. */
    private static String mapClassOf(ModelData data) throws Exception {
        Field field = ModelData.class.getDeclaredField("properties");
        field.setAccessible(true);
        Map<?, ?> properties = (Map<?, ?>) field.get(data);
        return properties.getClass().getSimpleName();
    }
}
