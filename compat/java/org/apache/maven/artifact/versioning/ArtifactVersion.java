/*
 * Часть nfcompat. Мини-замена `org.apache.maven.artifact.versioning.ArtifactVersion`.
 *
 * ПОЧЕМУ ЭТОТ ФАЙЛ ВООБЩЕ ЕСТЬ:
 * в NeoForge `IModInfo#getVersion()` возвращает именно `ArtifactVersion` (класс из
 * `org.apache.maven:maven-artifact`, который NeoForge тащит с собой). Живой аддон
 * компилируется под дескриптор
 *
 *     net/neoforged/neoforgespi/language/IModInfo.getVersion:()Lorg/apache/maven/artifact/versioning/ArtifactVersion;
 *
 * (проверено javap'ом на extendedae_plus-1.6.3: `ModCheckUtils.getVersion` берёт
 * `getMods().get(0)` -> `IModInfo.getVersion()` -> `ArtifactVersion.toString()`),
 * поэтому вернуть из `getVersion()` строку нельзя — будет NoSuchMethodError.
 * maven-artifact в наш classpath не приходит (в gradle-кэше его нет, в build.gradle
 * зависимости нет), тянуть настоящий артефакт ради одного `toString()` — лишний jar
 * в рантайме игры. Поэтому здесь лежит совместимый по сигнатурам минимум.
 *
 * ЧТО РЕАЛИЗОВАНО: полный набор методов интерфейса Maven (его немного), чтобы чужой
 * код, которому нужен `getMajorVersion()`/`compareTo`, не падал. Разбор версии —
 * упрощённый (см. DefaultArtifactVersion): числа по точкам + квалификатор после '-'.
 *
 * РИСК: если когда-нибудь в рантайм попадёт настоящий maven-artifact, классы
 * схлопнутся по имени и победит тот, что загрузится первым. Сейчас такой зависимости
 * нет ни у AE2, ни у Fabric API, ни у наших compileOnly-модов.
 */
package org.apache.maven.artifact.versioning;

/**
 * Версия артефакта (подмножество Maven, достаточное для IModInfo).
 */
public interface ArtifactVersion extends Comparable<ArtifactVersion> {

    /** Версия в том виде, как она записана. */
    String getVersionString();

    /** Переразобрать версию (в Maven метод мутирующий). */
    void parseVersion(String version);

    int getMajorVersion();

    int getMinorVersion();

    int getIncrementalVersion();

    int getBuildNumber();

    /** Квалификатор после дефиса (alpha, beta, ...) или null. */
    String getQualifier();

    @Override
    int compareTo(ArtifactVersion otherVersion);
}
